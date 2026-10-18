package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreArgument;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreBlock;
import dev.w0fv1.norm.core.CoreCollectionElement;
import dev.w0fv1.norm.core.CoreConformance;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionLink;
import dev.w0fv1.norm.core.CoreDefinitionOccurrence;
import dev.w0fv1.norm.core.CoreDefinitionRecord;
import dev.w0fv1.norm.core.CoreEnumVariant;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.CoreExpression;
import dev.w0fv1.norm.core.CoreFieldReference;
import dev.w0fv1.norm.core.CoreInterfaceHierarchy;
import dev.w0fv1.norm.core.CoreIteration;
import dev.w0fv1.norm.core.CoreLiveSchema;
import dev.w0fv1.norm.core.CoreLocal;
import dev.w0fv1.norm.core.CoreMethodDispatch;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CorePattern;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreRuntimeIdentity;
import dev.w0fv1.norm.core.CoreRuntimeType;
import dev.w0fv1.norm.core.CoreStatement;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeCapture;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypeRelations;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.CoreVisibility;
import dev.w0fv1.norm.core.CoreWitness;
import dev.w0fv1.norm.core.CoreWitnessTarget;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.value.LanguageMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class Lowerer {
  private final com.oracle.truffle.api.TruffleLanguage<?> language;
  private final Map<DefinitionOccurrenceId, RuntimeValues.AggregateInfo> aggregateInfo =
      new HashMap<>();
  private final Map<DefinitionOccurrenceId, CoreDefinition.Aggregate> aggregates =
      new LinkedHashMap<>();
  private final Map<DefinitionId, RuntimeValues.AggregateInfo> hostInterfaces =
      new LinkedHashMap<>();
  private final Map<DefinitionOccurrenceId, FunctionPlan> callables = new LinkedHashMap<>();
  private final Map<BuiltinTypeId, Map<DefinitionId, RuntimeValues.DispatchTarget>>
      builtinDispatch = new HashMap<>();
  private final Map<DocumentId, Source> sources = new HashMap<>();
  private CoreArtifact artifact;
  private CoreProgram program;
  private CoreTypeRelations typeRelations;
  private CoreRuntimeIdentity identity;
  private AnnotationRuntime annotations;
  private CoreExecutionPlan execution;
  private final CoreLiveSchema liveSchema;
  private final LiveBindings.Builder liveNodes;

  Lowerer(com.oracle.truffle.api.TruffleLanguage<?> language) {
    this(language, null);
  }

  Lowerer(com.oracle.truffle.api.TruffleLanguage<?> language, CoreLiveSchema liveSchema) {
    this.language = language;
    this.liveSchema = liveSchema;
    liveNodes = liveSchema == null ? null : new LiveBindings.Builder(liveSchema);
  }

  LiveBindings.Version liveVersion() {
    if (liveNodes == null) throw new IllegalStateException("lowering has no live bindings");
    return liveNodes.build();
  }

  ExecutableProgram lower(CoreArtifact checkedArtifact) {
    return lower(checkedArtifact, CoreExecutionPlan.forArtifact(checkedArtifact));
  }

  ExecutableProgram lower(CoreArtifact checkedArtifact, CoreExecutionPlan execution) {
    this.execution = Objects.requireNonNull(execution, "execution");
    artifact = Objects.requireNonNull(checkedArtifact, "checkedArtifact");
    program = artifact.program();
    identity = CoreRuntimeIdentity.create(artifact);
    typeRelations = new CoreTypeRelations(identity.definitions());
    annotations = new AnnotationRuntime(artifact, identity);
    indexDefinitions(this.execution);
    createCallTargets();
    indexDispatch();
    initializeAnnotations();
    lowerBodies();
    DefinitionOccurrenceId entry = artifact.entryPoint();
    FunctionPlan entryPlan = callables.get(entry);
    if (entryPlan == null) throw new IllegalStateException("entry callable is absent");
    Map<DefinitionId, com.oracle.truffle.api.CallTarget> bridgeTargets = new LinkedHashMap<>();
    for (FunctionPlan plan : callables.values()) {
      if (bridgeTargets.putIfAbsent(identity.slot(plan.id), plan.target) != null)
        throw new IllegalStateException("ambiguous Java execution slot");
    }
    GuestValueFactory values = guestValues();
    annotations.initializeValues(values);
    return new ExecutableProgram(
        entryPlan.target, annotations, values, annotations.program(), Map.copyOf(bridgeTargets));
  }

  private GuestValueFactory guestValues() {
    Map<DefinitionId, com.oracle.truffle.api.CallTarget> targets = new LinkedHashMap<>();
    callables.values().forEach(plan -> targets.putIfAbsent(plan.id.representative(), plan.target));
    List<GuestValueFactory.AggregatePlan> aggregatePlans = new ArrayList<>();
    for (Map.Entry<DefinitionOccurrenceId, CoreDefinition.Aggregate> entry :
        aggregates.entrySet()) {
      DefinitionId definition = entry.getKey().representative();
      List<GuestValueFactory.Initializer> initializers =
          entry.getValue().constructors().stream()
              .map(link -> resolve(definition, link))
              .filter(execution.callables()::contains)
              .map(
                  constructor -> {
                    com.oracle.truffle.api.CallTarget target = targets.get(constructor);
                    if (target == null) {
                      throw new IllegalStateException("aggregate initializer is absent");
                    }
                    CoreDefinition constructorDefinition =
                        program.definition(constructor).orElseThrow();
                    if (!(constructorDefinition instanceof CoreDefinition.Callable callable)) {
                      throw new IllegalStateException("aggregate initializer is not callable");
                    }
                    return new GuestValueFactory.Initializer(
                        target,
                        dev.w0fv1.norm.core.CoreConstructorParameters.forCallable(callable));
                  })
              .toList();
      aggregatePlans.add(
          GuestValueFactory.AggregatePlan.create(
              identity.id(definition),
              (CoreDefinition.Aggregate) identity.definition(definition, entry.getValue()),
              aggregateInfo.get(entry.getKey()),
              initializers));
    }
    List<GuestValueFactory.EnumPlan> enumPlans =
        program.definitions().stream()
            .filter(record -> record.definition() instanceof CoreDefinition.Enum)
            .map(
                record ->
                    GuestValueFactory.EnumPlan.create(
                        identity.id(record.id()),
                        (CoreDefinition.Enum)
                            identity.definition(record.id(), record.definition())))
            .toList();
    return new GuestValueFactory(aggregatePlans, hostInterfaces, enumPlans, annotations);
  }

  private void indexDefinitions(CoreExecutionPlan execution) {
    for (CoreDefinitionOccurrence occurrence : artifact.authoring().occurrences()) {
      CoreDefinition definition =
          program.definition(occurrence.id().representative()).orElseThrow();
      switch (definition) {
        case CoreDefinition.Aggregate declaration -> aggregates.put(occurrence.id(), declaration);
        case CoreDefinition.Callable declaration -> {
          if (execution.callables().contains(occurrence.id().representative()))
            callables.put(occurrence.id(), plan(occurrence.id(), declaration));
        }
        case CoreDefinition.Enum ignored -> {}
        case CoreDefinition.Interface ignored -> {}
        case CoreDefinition.MethodSignature ignored -> {}
        case CoreDefinition.BuiltinConformance ignored -> {}
      }
    }
  }

  private FunctionPlan plan(DefinitionOccurrenceId id, CoreDefinition.Callable declaration) {
    FunctionPlan plan =
        new FunctionPlan(id, declaration, liveSchema == null ? null : liveSchema.layout(id));
    for (CoreLocal local : declaration.locals()) plan.allocate(local);
    if (declaration.hasReceiver()) plan.arguments.add(plan.binding(0));
    declaration.captureLocals().forEach(local -> plan.arguments.add(plan.binding(local)));
    declaration.parameterLocals().forEach(local -> plan.arguments.add(plan.binding(local)));
    declaration.reifiedTypeLocals().forEach(local -> plan.arguments.add(plan.binding(local)));
    plan.descriptor = plan.frame.build();
    return plan;
  }

  private void createCallTargets() {
    for (FunctionPlan plan : callables.values()) {
      plan.root =
          new FunctionRootNode(
              language,
              artifact.displayName(plan.id),
              plan.id,
              plan.descriptor,
              plan.arguments.toArray(FrameBinding[]::new),
              artifact.authoring().occurrence(plan.id).orElseThrow().role()
                      == dev.w0fv1.norm.core.CoreDefinitionRole.CONSTRUCTOR
                  ? java.util.Optional.of(
                      dev.w0fv1.norm.core.CoreConstructorParameters.forCallable(plan.declaration))
                  : java.util.Optional.empty(),
              section(plan.id, 0));
      plan.implementation = plan.root.getCallTarget();
      plan.target = plan.implementation;
    }
    for (FunctionPlan plan : callables.values()) {
      if (plan.declaration.interceptors().isEmpty()
          && plan.declaration.parameters().stream()
              .allMatch(parameter -> parameter.interceptors().isEmpty())) continue;
      plan.target =
          new CallableInterceptorRootNode(
                  language,
                  artifact.displayName(plan.id),
                  section(plan.id, 0),
                  plan.id,
                  plan.implementation,
                  plan.declaration,
                  artifact.authoring().occurrence(plan.id).orElseThrow().role()
                      == dev.w0fv1.norm.core.CoreDefinitionRole.CONSTRUCTOR,
                  identity.type(plan.id.representative(), plan.declaration.returnType()),
                  annotations)
              .getCallTarget();
    }
    if (liveNodes != null) {
      for (FunctionPlan plan : callables.values())
        plan.target = liveNodes.callable(plan.id, plan.target);
    }
  }

  private void initializeAnnotations() {
    Map<DefinitionOccurrenceId, com.oracle.truffle.api.CallTarget> targets = new LinkedHashMap<>();
    callables.values().forEach(plan -> targets.put(plan.id, plan.target));
    annotations.initialize(aggregateInfo, targets);
  }

  private void indexDispatch() {
    Map<DefinitionId, FunctionPlan> callableByDefinition = new HashMap<>();
    callables
        .values()
        .forEach(plan -> callableByDefinition.putIfAbsent(plan.id.representative(), plan));
    Map<DefinitionId, DefinitionOccurrenceId> aggregateOccurrences = new HashMap<>();
    aggregates
        .keySet()
        .forEach(occurrence -> aggregateOccurrences.put(occurrence.representative(), occurrence));
    for (Map.Entry<DefinitionOccurrenceId, CoreDefinition.Aggregate> entry :
        aggregates.entrySet()) {
      DefinitionOccurrenceId occurrence = entry.getKey();
      Map<DefinitionId, RuntimeValues.DispatchTarget> dispatch = new HashMap<>();
      Map<DefinitionId, RuntimeValues.DispatchTarget> methodTargets = new HashMap<>();
      for (CoreMethodDispatch method : entry.getValue().dispatch()) {
        DefinitionId sourceSlot = resolve(occurrence.representative(), method.slot());
        if (!execution.dispatchSlots().contains(sourceSlot)) continue;
        DefinitionId implementation = resolve(occurrence.representative(), method.target());
        for (DefinitionOccurrenceId slotOccurrence : identity.occurrences(sourceSlot)) {
          DefinitionId slot = identity.slot(slotOccurrence);
          RuntimeValues.DispatchTarget target;
          if (program.definition(implementation).orElseThrow()
              instanceof CoreDefinition.MethodSignature) {
            target = new RuntimeValues.DispatchTarget.HostMethod(identity.slot(implementation));
          } else {
            FunctionPlan plan = memberPlan(implementation, identity.name(slotOccurrence));
            CoreType receiverType =
                identity.type(occurrence.representative(), method.receiverType());
            target =
                new RuntimeValues.DispatchTarget.Callable(
                    plan.target, ((CoreType.Declared) receiverType).arguments());
          }
          dispatch.put(slot, target);
          methodTargets.put(slot, target);
        }
      }
      List<CoreType> rootArguments =
          java.util.stream.IntStream.range(0, entry.getValue().typeParameters().size())
              .mapToObj(index -> (CoreType) new CoreType.Parameter(index, CoreNullability.NON_NULL))
              .toList();
      for (RuntimeConformance inherited :
          aggregateConformances(occurrence.representative(), entry.getValue(), rootArguments)) {
        CoreConformance conformance = inherited.conformance();
        for (CoreWitness witness : conformance.witnesses()) {
          DefinitionId requirement = resolve(inherited.owner(), witness.requirement());
          if (!execution.dispatchSlots().contains(requirement)) continue;
          DefinitionId implementation =
              witness.implementation() instanceof CoreWitnessTarget.Callable callable
                  ? resolve(inherited.owner(), callable.definition())
                  : null;
          RuntimeValues.DispatchTarget target = methodTargets.get(implementation);
          if (target == null) {
            target = lowerWitnessTarget(inherited.owner(), witness.implementation(), requirement);
          }
          List<CoreType> defaultReceiverArguments =
              defaultWitnessReceiverArguments(
                  inherited.owner(), witness.implementation(), inherited.interfaceType());
          if (target instanceof RuntimeValues.DispatchTarget.Callable callable
              && defaultReceiverArguments != null) {
            target =
                new RuntimeValues.DispatchTarget.Callable(
                    callable.target(), defaultReceiverArguments);
          }
          RuntimeValues.DispatchTarget previous =
              dispatch.putIfAbsent(identity.slot(requirement), target);
          if (previous != null && !previous.equals(target)) {
            throw new IllegalStateException("verified aggregate dispatch is conflicting");
          }
        }
      }
      aggregateInfo.put(
          occurrence,
          new RuntimeValues.AggregateInfo(
              identity.id(occurrence.representative()),
              artifact.displayName(occurrence),
              entry.getValue().fieldCount(),
              fieldPlans(occurrence, entry.getValue(), aggregateOccurrences),
              dispatch,
              aggregateAncestors(occurrence.representative()).stream()
                  .map(identity::id)
                  .collect(java.util.stream.Collectors.toUnmodifiableSet()),
              annotations));
    }
    for (CoreDefinitionRecord record : program.definitions()) {
      if (!(record.definition() instanceof CoreDefinition.BuiltinConformance conformance)) continue;
      CoreType concrete =
          CoreTypes.absolute(conformance.concreteBuiltinType(), record.id(), program);
      CoreType.Declared declared = (CoreType.Declared) concrete;
      BuiltinTypeId builtin = ((CoreTypeConstructor.Builtin) declared.constructor()).id();
      Map<DefinitionId, RuntimeValues.DispatchTarget> dispatch =
          builtinDispatch.computeIfAbsent(builtin, ignored -> new HashMap<>());
      for (CoreWitness witness : conformance.witnesses()) {
        DefinitionId requirement = resolve(record.id(), witness.requirement());
        if (!execution.dispatchSlots().contains(requirement)) continue;
        RuntimeValues.DispatchTarget target =
            lowerWitnessTarget(record.id(), witness.implementation(), requirement);
        if (target instanceof RuntimeValues.DispatchTarget.Callable callable) {
          CoreType interfaceType = identity.type(record.id(), conformance.interfaceType());
          target =
              new RuntimeValues.DispatchTarget.Callable(
                  callable.target(), ((CoreType.Declared) interfaceType).arguments());
        }
        if (dispatch.putIfAbsent(identity.slot(requirement), target) != null) {
          throw new IllegalStateException("verified builtin dispatch is duplicated");
        }
      }
    }
    indexHostInterfaces(callableByDefinition);
    indexJavaApplicationInterfaces();
  }

  private void indexJavaApplicationInterfaces() {
    for (CoreDefinitionRecord record : program.definitions()) {
      if (!(record.definition() instanceof CoreDefinition.Interface declared)) continue;
      Map<DefinitionId, RuntimeValues.DispatchTarget> dispatch = new LinkedHashMap<>();
      java.util.LinkedHashSet<DefinitionId> ancestors = new java.util.LinkedHashSet<>();
      List<CoreType> arguments =
          java.util.stream.IntStream.range(0, declared.typeParameters().size())
              .mapToObj(index -> (CoreType) new CoreType.Parameter(index, CoreNullability.NON_NULL))
              .toList();
      indexJavaApplicationInterface(record.id(), declared, arguments, dispatch, ancestors);
      hostInterfaces.putIfAbsent(
          identity.id(record.id()),
          new RuntimeValues.AggregateInfo(
              identity.id(record.id()),
              declared.nominalType().name(),
              0,
              List.of(),
              dispatch,
              ancestors.stream()
                  .map(identity::id)
                  .collect(java.util.stream.Collectors.toUnmodifiableSet()),
              annotations));
    }
  }

  private void indexJavaApplicationInterface(
      DefinitionId definition,
      CoreDefinition.Interface declared,
      List<CoreType> arguments,
      Map<DefinitionId, RuntimeValues.DispatchTarget> dispatch,
      java.util.Set<DefinitionId> ancestors) {
    if (!ancestors.add(definition)) return;
    RuntimeValues.AggregateInfo binding = hostInterfaces.get(identity.id(definition));
    for (CoreDefinitionLink link : declared.declaredMethods()) {
      DefinitionId method = resolve(definition, link);
      if (!execution.dispatchSlots().contains(method)) continue;
      RuntimeValues.DispatchTarget target =
          binding == null ? null : binding.dispatch().get(identity.slot(method));
      if (target instanceof RuntimeValues.DispatchTarget.Callable callable) {
        List<CoreType> receiverArguments =
            callable.specializedReceiverTypeArguments()
                ? callable.receiverTypeArguments().stream()
                    .map(type -> type.substitute(arguments::get))
                    .toList()
                : arguments;
        target = new RuntimeValues.DispatchTarget.Callable(callable.target(), receiverArguments);
      }
      dispatch.putIfAbsent(
          identity.slot(method),
          target == null
              ? new RuntimeValues.DispatchTarget.HostMethod(identity.slot(method))
              : target);
    }
    for (CoreType parentType : declared.directParents()) {
      CoreType parent =
          CoreTypes.absolute(parentType, definition, program).substitute(arguments::get);
      CoreType.Declared parentDeclared = (CoreType.Declared) parent;
      DefinitionId parentDefinition =
          ((DefinitionReference.External)
                  ((CoreTypeConstructor.User) parentDeclared.constructor()).definition())
              .definition();
      CoreDefinition parentValue = program.definition(parentDefinition).orElseThrow();
      indexJavaApplicationInterface(
          parentDefinition,
          (CoreDefinition.Interface) parentValue,
          parentDeclared.arguments(),
          dispatch,
          ancestors);
    }
  }

  private void indexHostInterfaces(Map<DefinitionId, FunctionPlan> callableByDefinition) {
    for (CoreDefinitionRecord record : program.definitions()) {
      if (!(record.definition() instanceof CoreDefinition.Aggregate aggregate)
          || aggregate.fieldCount() != 0
          || !aggregate.nominalType().name().endsWith("BindingValue")) {
        continue;
      }
      for (CoreConformance conformance : aggregate.conformances()) {
        CoreType interfaceType =
            CoreTypes.absolute(conformance.interfaceType(), record.id(), program);
        if (!(interfaceType instanceof CoreType.Declared declared)
            || !(declared.constructor() instanceof CoreTypeConstructor.User user)
            || !(user.definition() instanceof DefinitionReference.External external)
            || !(program.definition(external.definition()).orElseThrow()
                instanceof CoreDefinition.Interface implemented)) {
          continue;
        }
        Map<DefinitionId, RuntimeValues.DispatchTarget> dispatch = new LinkedHashMap<>();
        for (CoreWitness witness : conformance.witnesses()) {
          DefinitionId requirement = resolve(record.id(), witness.requirement());
          if (!execution.dispatchSlots().contains(requirement)) continue;
          RuntimeValues.DispatchTarget target;
          if (witness.implementation() instanceof CoreWitnessTarget.Callable callable) {
            DefinitionId implementation = resolve(record.id(), callable.definition());
            FunctionPlan plan = callableByDefinition.get(implementation);
            if (plan == null) {
              throw new IllegalStateException("host interface dispatch target is absent");
            }
            target = new RuntimeValues.DispatchTarget.Callable(plan.target);
          } else {
            target =
                new RuntimeValues.DispatchTarget.Intrinsic(
                    ((CoreWitnessTarget.Intrinsic) witness.implementation()).intrinsic());
          }
          List<CoreType> receiverArguments =
              defaultWitnessReceiverArguments(record.id(), witness.implementation(), interfaceType);
          if (target instanceof RuntimeValues.DispatchTarget.Callable callable
              && receiverArguments != null) {
            target =
                new RuntimeValues.DispatchTarget.Callable(callable.target(), receiverArguments);
          }
          dispatch.put(identity.slot(requirement), target);
        }
        RuntimeValues.AggregateInfo info =
            new RuntimeValues.AggregateInfo(
                identity.id(external.definition()),
                implemented.nominalType().name(),
                0,
                List.of(),
                dispatch,
                java.util.Set.of(identity.id(external.definition())),
                annotations);
        RuntimeValues.AggregateInfo previous =
            hostInterfaces.putIfAbsent(identity.id(external.definition()), info);
        if (previous != null && !previous.dispatch().equals(info.dispatch())) {
          throw new IllegalStateException("host interface dispatch is conflicting");
        }
      }
    }
  }

  private List<RuntimeValues.FieldPlan> fieldPlans(
      DefinitionOccurrenceId occurrence,
      CoreDefinition.Aggregate aggregate,
      Map<DefinitionId, DefinitionOccurrenceId> occurrences) {
    List<RuntimeValues.FieldPlan> result = new ArrayList<>();
    if (aggregate.parentType().isPresent()) {
      CoreType parent =
          CoreTypes.absolute(
              aggregate.parentType().orElseThrow(), occurrence.representative(), program);
      DefinitionId parentId =
          ((DefinitionReference.External)
                  ((CoreTypeConstructor.User) ((CoreType.Declared) parent).constructor())
                      .definition())
              .definition();
      DefinitionOccurrenceId parentOccurrence = occurrences.get(parentId);
      if (parentOccurrence == null) {
        throw new IllegalStateException("aggregate parent occurrence is absent");
      }
      var arguments =
          ((CoreType.Declared) parent)
              .arguments().stream()
                  .map(type -> identity.type(occurrence.representative(), type))
                  .toList();
      result.addAll(
          fieldPlans(
                  parentOccurrence,
                  (CoreDefinition.Aggregate) program.definition(parentId).orElseThrow(),
                  occurrences)
              .stream()
              .map(field -> field.project(arguments))
              .toList());
    }
    aggregate
        .fields()
        .forEach(
            field ->
                result.add(
                    new RuntimeValues.FieldPlan(
                        new DefinitionOccurrenceId(
                            identity.id(occurrence.representative()), occurrence.ordinal()),
                        field.name(),
                        field.ordinal(),
                        field.visibility() == CoreVisibility.PUBLIC,
                        field.interceptors(),
                        identity.type(occurrence.representative(), field.type()),
                        field
                            .initializer()
                            .map(
                                initializer -> {
                                  DefinitionOccurrenceId target =
                                      artifact
                                          .authoring()
                                          .target(occurrence, initializer.nodeIndex());
                                  FunctionPlan plan = callables.get(target);
                                  if (plan == null)
                                    throw new IllegalStateException(
                                        "field initializer target is absent");
                                  return (com.oracle.truffle.api.CallTarget) plan.target;
                                }))));
    return List.copyOf(result);
  }

  private FunctionPlan memberPlan(DefinitionId definition, String name) {
    List<FunctionPlan> matches =
        identity.occurrences(definition).stream()
            .map(callables::get)
            .filter(Objects::nonNull)
            .filter(plan -> identity.name(plan.id).equals(name))
            .toList();
    if (matches.size() == 1) return matches.getFirst();
    List<FunctionPlan> all =
        identity.occurrences(definition).stream()
            .map(callables::get)
            .filter(Objects::nonNull)
            .toList();
    if (all.size() == 1) return all.getFirst();
    throw new IllegalStateException("method occurrence is ambiguous: " + name);
  }

  private RuntimeValues.DispatchTarget lowerWitnessTarget(
      DefinitionId owner, CoreWitnessTarget target, DefinitionId requirement) {
    return switch (target) {
      case CoreWitnessTarget.Callable callable -> {
        FunctionPlan plan =
            memberPlan(
                resolve(owner, callable.definition()),
                identity.name(identity.occurrences(requirement).getFirst()));
        if (plan == null) throw new IllegalStateException("witness callable target is absent");
        yield new RuntimeValues.DispatchTarget.Callable(plan.target);
      }
      case CoreWitnessTarget.Intrinsic intrinsic ->
          new RuntimeValues.DispatchTarget.Intrinsic(intrinsic.intrinsic());
    };
  }

  private List<RuntimeConformance> aggregateConformances(
      DefinitionId owner, CoreDefinition.Aggregate aggregate, List<CoreType> ownerArguments) {
    List<RuntimeConformance> result = new ArrayList<>();
    for (CoreConformance conformance : aggregate.conformances()) {
      CoreType interfaceType =
          CoreTypes.absolute(conformance.interfaceType(), owner, program)
              .substitute(ownerArguments::get);
      result.add(new RuntimeConformance(owner, conformance, interfaceType));
    }
    if (aggregate.parentType().isPresent()) {
      CoreType parent =
          CoreTypes.absolute(aggregate.parentType().orElseThrow(), owner, program)
              .substitute(ownerArguments::get);
      CoreType.Declared declared = (CoreType.Declared) parent;
      DefinitionId parentId =
          ((DefinitionReference.External)
                  ((CoreTypeConstructor.User) declared.constructor()).definition())
              .definition();
      CoreDefinition parentDefinition = program.definition(parentId).orElseThrow();
      result.addAll(
          aggregateConformances(
              parentId, (CoreDefinition.Aggregate) parentDefinition, declared.arguments()));
    }
    return List.copyOf(result);
  }

  private record RuntimeConformance(
      DefinitionId owner, CoreConformance conformance, CoreType interfaceType) {}

  private List<CoreType> defaultWitnessReceiverArguments(
      DefinitionId owner, CoreWitnessTarget target, CoreType interfaceType) {
    if (!(target instanceof CoreWitnessTarget.Callable callable)) return null;
    DefinitionId implementationId = resolve(owner, callable.definition());
    CoreDefinition implementation = program.definition(implementationId).orElseThrow();
    if (!(implementation instanceof CoreDefinition.Callable method)
        || method.receiverType().isEmpty()) {
      return null;
    }
    CoreType receiver =
        CoreTypes.absolute(method.receiverType().orElseThrow(), implementationId, program);
    if (!(receiver instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(user.definition() instanceof DefinitionReference.External external)
        || external.definition().equals(owner)) {
      return null;
    }
    return CoreInterfaceHierarchy.arguments(program, owner, interfaceType, external.definition());
  }

  private java.util.Set<DefinitionId> aggregateAncestors(DefinitionId definition) {
    java.util.LinkedHashSet<DefinitionId> result = new java.util.LinkedHashSet<>();
    DefinitionId current = definition;
    while (result.add(current)) {
      CoreDefinition value = program.definition(current).orElseThrow();
      if (!(value instanceof CoreDefinition.Aggregate aggregate)
          || aggregate.parentType().isEmpty()) {
        break;
      }
      CoreType parent = CoreTypes.absolute(aggregate.parentType().orElseThrow(), current, program);
      CoreType.Declared declared = (CoreType.Declared) parent;
      current =
          ((DefinitionReference.External)
                  ((CoreTypeConstructor.User) declared.constructor()).definition())
              .definition();
    }
    return java.util.Set.copyOf(result);
  }

  private void lowerBodies() {
    callables
        .values()
        .forEach(plan -> plan.root.initialize(lowerBlock(plan.declaration.body(), plan)));
  }

  private StatementNode lowerBlock(CoreBlock block, FunctionPlan plan) {
    StatementNode[] statements =
        block.statements().stream()
            .map(statement -> lowerStatement(statement, plan))
            .toArray(StatementNode[]::new);
    StatementNode lowered =
        new StatementNodes.Block(statements)
            .at(section(plan.id, block.nodeIndex()), plan.id, block.nodeIndex());
    if (liveNodes == null) return lowered;
    String key = plan.layout.block(block.nodeIndex());
    return liveNodes
        .block(
            new LiveBindings.Key(liveSchema.key(plan.id), key),
            lowered,
            plan.layout.required(key).stream().map(plan::binding).toList())
        .at(section(plan.id, block.nodeIndex()), plan.id, block.nodeIndex());
  }

  private StatementNode lowerStatement(CoreStatement statement, FunctionPlan plan) {
    StatementNode lowered =
        switch (statement) {
          case CoreStatement.LocalDeclaration local ->
              new StatementNodes.WriteLocal(
                  plan.binding(local.localIndex()), lowerExpression(local.initializer(), plan));
          case CoreStatement.LocalAssignment assignment ->
              new StatementNodes.WriteLocal(
                  plan.binding(assignment.localIndex()), lowerExpression(assignment.value(), plan));
          case CoreStatement.FieldAssignment assignment ->
              new StatementNodes.WriteField(
                  lowerExpression(assignment.receiver(), plan),
                  fieldKey(plan, assignment.field()),
                  lowerExpression(assignment.value(), plan));
          case CoreStatement.IntrinsicAssignment assignment -> {
            List<ExpressionNode> arguments = new ArrayList<>();
            assignment.index().ifPresent(value -> arguments.add(lowerExpression(value, plan)));
            arguments.add(lowerExpression(assignment.value(), plan));
            yield new StatementNodes.IntrinsicWrite(
                assignment.intrinsic(),
                lowerExpression(assignment.receiver(), plan),
                arguments.toArray(ExpressionNode[]::new));
          }
          case CoreStatement.ReferenceAssignment assignment ->
              new StatementNodes.WriteReference(
                  lowerExpression(assignment.reference(), plan),
                  lowerExpression(assignment.value(), plan));
          case CoreStatement.ExpressionStatement expression ->
              new StatementNodes.ExpressionStatement(
                  lowerExpression(expression.expression(), plan));
          case CoreStatement.IfStatement conditional ->
              new StatementNodes.If(
                  lowerExpression(conditional.condition(), plan),
                  lowerBlock(conditional.thenBlock(), plan),
                  lowerBlock(conditional.elseBlock(), plan));
          case CoreStatement.ForStatement loop -> {
            IterationNodes nodes = iterationNodes(loop.iteration(), plan);
            yield new StatementNodes.For(
                plan.binding(loop.iteratorLocal()),
                plan.binding(loop.variableLocal()),
                loop.indexLocal().isPresent()
                    ? java.util.Optional.of(plan.binding(loop.indexLocal().orElseThrow()))
                    : java.util.Optional.empty(),
                lowerExpression(loop.iterable(), plan),
                lowerBlock(loop.body(), plan),
                nodes.factory(),
                nodes.cursor());
          }
          case CoreStatement.ConditionalForStatement loop ->
              new StatementNodes.ConditionalFor(
                  lowerExpression(loop.condition(), plan), lowerBlock(loop.body(), plan));
          case CoreStatement.TryStatement tried ->
              new StatementNodes.Try(
                  lowerBlock(tried.body(), plan),
                  tried.catches().stream()
                      .map(
                          clause ->
                              new StatementNodes.CatchHandler(
                                  catchDefinition(plan.id.representative(), clause.type()),
                                  plan.binding(clause.localIndex()),
                                  lowerBlock(clause.body(), plan)))
                      .toArray(StatementNodes.CatchHandler[]::new),
                  tried.finallyBlock().map(value -> lowerBlock(value, plan)).orElse(null));
          case CoreStatement.ThrowStatement thrown ->
              new StatementNodes.Throw(lowerExpression(thrown.exception(), plan));
          case CoreStatement.ReturnStatement returned ->
              new StatementNodes.Return(
                  returned.value().map(value -> lowerExpression(value, plan)).orElse(null));
          case CoreStatement.YieldStatement yielded ->
              new StatementNodes.Yield(lowerExpression(yielded.value(), plan));
          case CoreStatement.BreakStatement ignored -> new StatementNodes.Break();
          case CoreStatement.ContinueStatement ignored -> new StatementNodes.Continue();
        };
    lowered.at(section(plan.id, statement.nodeIndex()), plan.id, statement.nodeIndex());
    if (liveNodes != null && statement instanceof CoreStatement.LocalDeclaration local) {
      liveNodes.initializer(
          new LiveBindings.Key(liveSchema.key(plan.id), plan.layout.local(local.localIndex())),
          lowered,
          plan.layout.required(plan.layout.statement(statement.nodeIndex())).stream()
              .map(plan::binding)
              .toList());
    }
    if (liveNodes == null) return lowered;
    String key = plan.layout.statement(statement.nodeIndex());
    return liveNodes
        .statement(
            new LiveBindings.Key(liveSchema.key(plan.id), key),
            lowered,
            plan.layout.required(key).stream().map(plan::binding).toList(),
            statement instanceof CoreStatement.FieldAssignment
                || statement instanceof CoreStatement.ReferenceAssignment
                || statement instanceof CoreStatement.IntrinsicAssignment)
        .at(section(plan.id, statement.nodeIndex()), plan.id, statement.nodeIndex());
  }

  private RuntimeValues.FieldKey fieldKey(FunctionPlan plan, CoreFieldReference reference) {
    DefinitionId owner = resolve(plan.id.representative(), reference.owner());
    CoreDefinition.Aggregate aggregate =
        (CoreDefinition.Aggregate) program.definition(owner).orElseThrow();
    String name =
        aggregate.fields().stream()
            .filter(field -> field.ordinal() == reference.ordinal())
            .findFirst()
            .orElseThrow()
            .name();
    return new RuntimeValues.FieldKey(identity.id(owner), name);
  }

  private DefinitionId catchDefinition(DefinitionId owner, CoreType type) {
    CoreType absolute = identity.type(owner, type);
    CoreType.Declared declared = (CoreType.Declared) absolute;
    return ((DefinitionReference.External)
            ((CoreTypeConstructor.User) declared.constructor()).definition())
        .definition();
  }

  private ExpressionNode lowerExpression(CoreExpression expression, FunctionPlan plan) {
    ExpressionNode lowered =
        switch (expression) {
          case CoreExpression.Literal literal ->
              new ExpressionNodes.Literal(
                  literal.type().equals(CoreType.CODE_POINT)
                      ? new RuntimeValues.CodePointValue(((Number) literal.value()).intValue())
                      : literal.value());
          case CoreExpression.NullLiteral ignored -> new ExpressionNodes.NullLiteral();
          case CoreExpression.CollectionLiteral collection ->
              new ExpressionNodes.CollectionLiteral(
                  collection.materializer(),
                  collection.elements().stream()
                      .map(value -> lowerCollectionElement(value, plan))
                      .toArray(CollectionElementNode[]::new),
                  lowerRuntimeType(collection.runtimeType(), plan));
          case CoreExpression.LocalRead local ->
              new ExpressionNodes.ReadLocal(plan.binding(local.localIndex()));
          case CoreExpression.ParameterProvided provided ->
              new ExpressionNodes.ParameterProvided(provided.parameterIndex());
          case CoreExpression.Let let ->
              new ExpressionNodes.Let(
                  plan.binding(let.localIndex()),
                  lowerExpression(let.initializer(), plan),
                  lowerExpression(let.body(), plan));
          case CoreExpression.FieldRead field ->
              new ExpressionNodes.ReadField(
                  lowerExpression(field.receiver(), plan),
                  fieldKey(plan, field.field()),
                  field.nullSafe());
          case CoreExpression.AddressLocal address ->
              new ExpressionNodes.AddressLocal(plan.binding(address.localIndex()));
          case CoreExpression.AddressField address ->
              new ExpressionNodes.AddressField(
                  lowerExpression(address.receiver(), plan), fieldKey(plan, address.field()));
          case CoreExpression.Dereference dereference ->
              new ExpressionNodes.Dereference(lowerExpression(dereference.reference(), plan));
          case CoreExpression.EnumConstruct construct -> lowerEnumConstruct(construct, plan);
          case CoreExpression.Unary unary ->
              switch (unary.operator()) {
                case NOT -> new ExpressionNodes.Not(lowerExpression(unary.operand(), plan));
                case NEGATE -> new ExpressionNodes.Negate(lowerExpression(unary.operand(), plan));
                case THROW -> new ExpressionNodes.Throw(lowerExpression(unary.operand(), plan));
                case NON_NULL ->
                    new ExpressionNodes.NonNull(lowerExpression(unary.operand(), plan));
              };
          case CoreExpression.Binary binary -> lowerBinary(binary, plan);
          case CoreExpression.Switch switched -> lowerSwitch(switched, plan);
          case CoreExpression.Index index ->
              new ExpressionNodes.Intrinsic(
                  index.readIntrinsic(),
                  lowerExpression(index.receiver(), plan),
                  new ExpressionNode[] {lowerExpression(index.index(), plan)},
                  new int[] {0},
                  null,
                  false);
          case CoreExpression.CopyObject copied ->
              new ExpressionNodes.CopyObject(
                  lowerExpression(copied.receiver(), plan), copied.nullSafe());
          case CoreExpression.Closure closure -> lowerClosure(closure, plan);
          case CoreExpression.Invoke invoke ->
              new ExpressionNodes.Invoke(
                  lowerExpression(invoke.callee(), plan),
                  lowerArguments(invoke.arguments(), plan),
                  parameterIndices(invoke.arguments()));
          case CoreExpression.Call call -> lowerCall(call, plan);
          case CoreExpression.InterfaceCall call -> lowerInterfaceCall(call, plan);
          case CoreExpression.Construct construct -> lowerConstruct(construct, plan);
          case CoreExpression.Intrinsic intrinsic ->
              new ExpressionNodes.Intrinsic(
                  intrinsic.intrinsic(),
                  intrinsic.receiver().map(value -> lowerExpression(value, plan)).orElse(null),
                  lowerArguments(intrinsic.arguments(), plan),
                  parameterIndices(intrinsic.arguments()),
                  intrinsic.runtimeType().map(value -> lowerRuntimeType(value, plan)).orElse(null),
                  intrinsic.nullSafe());
        };
    lowered.at(section(plan.id, expression.nodeIndex()), plan.id, expression.nodeIndex());
    return liveNodes == null
        ? lowered
        : liveNodes
            .expression(
                new LiveBindings.Key(
                    liveSchema.key(plan.id), plan.layout.expression(expression.nodeIndex())),
                lowered,
                plan.layout.required(plan.layout.expression(expression.nodeIndex())).stream()
                    .map(plan::binding)
                    .toList(),
                plan.layout.writesState(plan.layout.expression(expression.nodeIndex())),
                plan.layout.isControlBoundaryExpression(expression.nodeIndex()))
            .at(section(plan.id, expression.nodeIndex()), plan.id, expression.nodeIndex());
  }

  private ExpressionNode lowerSwitch(CoreExpression.Switch switched, FunctionPlan plan) {
    return new ExpressionNodes.Switch(
        lowerExpression(switched.value(), plan),
        switched.cases().stream()
            .map(value -> lowerPattern(value.pattern(), switched.value().type(), plan))
            .toArray(PatternNode[]::new),
        switched.cases().stream()
            .map(value -> lowerBlock(value.body(), plan))
            .toArray(StatementNode[]::new));
  }

  private PatternNode lowerPattern(CorePattern pattern, CoreType expected, FunctionPlan plan) {
    return switch (pattern) {
      case CorePattern.Variant variant -> {
        List<CoreType> payloadTypes = variantPayloadTypes(expected, variant.variantKey(), plan);
        PatternNode[] arguments = new PatternNode[variant.arguments().size()];
        for (int index = 0; index < arguments.length; index++) {
          arguments[index] =
              lowerPattern(variant.arguments().get(index), payloadTypes.get(index), plan);
        }
        yield new PatternNodes.Variant(variant.variantKey(), arguments);
      }
      case CorePattern.Binding binding ->
          new PatternNodes.Binding(
              plan.binding(binding.localIndex()),
              lowerRuntimeType(binding.runtimeType(), plan),
              typeRelations);
      case CorePattern.Wildcard ignored -> new PatternNodes.Wildcard();
      case CorePattern.Literal literal -> {
        CoreType actual =
            CoreTypes.absolute(nonNullable(expected), plan.id.representative(), program);
        Object value =
            actual.equals(CoreType.CODE_POINT)
                ? new RuntimeValues.CodePointValue(((Number) literal.value()).intValue())
                : literal.value();
        yield new PatternNodes.Literal(value);
      }
      case CorePattern.Null ignored -> new PatternNodes.Null();
    };
  }

  private List<CoreType> variantPayloadTypes(
      CoreType expected, String variantKey, FunctionPlan plan) {
    CoreType absolute =
        CoreTypes.absolute(nonNullable(expected), plan.id.representative(), program);
    if (!(absolute instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(user.definition() instanceof DefinitionReference reference)) {
      throw new IllegalStateException("verified variant pattern type is not an enum");
    }
    DefinitionId definition = program.resolve(plan.id.representative(), reference);
    CoreDefinition target = program.definition(definition).orElseThrow();
    if (!(target instanceof CoreDefinition.Enum enumDeclaration)) {
      throw new IllegalStateException("verified variant pattern type is not an enum");
    }
    CoreEnumVariant variant =
        enumDeclaration.variants().stream()
            .filter(value -> value.key().equals(variantKey))
            .findFirst()
            .orElseThrow();
    return variant.fields().stream()
        .map(field -> identity.type(definition, field.type()).substitute(declared.arguments()::get))
        .toList();
  }

  private static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          new CoreType.Declared(
              declared.constructor(),
              declared.arguments(),
              declared.category(),
              CoreNullability.NON_NULL);
      case CoreType.Function function ->
          new CoreType.Function(
              function.returnType(), function.parameterTypes(), CoreNullability.NON_NULL);
      case CoreType.Parameter parameter ->
          new CoreType.Parameter(parameter.index(), CoreNullability.NON_NULL);
      case CoreType.Reference reference -> reference;
      case CoreType.Special special -> special;
    };
  }

  private ExpressionNode lowerEnumConstruct(
      CoreExpression.EnumConstruct construct, FunctionPlan plan) {
    DefinitionOccurrenceId target = resolve(plan, construct.nodeIndex(), construct.target());
    return new ExpressionNodes.EnumConstruct(
        identity.id(target.representative()),
        artifact.displayName(target),
        construct.variantKey(),
        lowerRuntimeType(construct.runtimeType(), plan),
        lowerArguments(construct.arguments(), plan),
        parameterIndices(construct.arguments()));
  }

  private ExpressionNode lowerConstruct(CoreExpression.Construct construct, FunctionPlan plan) {
    DefinitionOccurrenceId target = resolve(plan, construct.nodeIndex(), construct.target());
    RuntimeValues.AggregateInfo info = aggregateInfo.get(target);
    if (info == null) throw new IllegalStateException("core aggregate target is absent: " + target);
    DefinitionId initializerId = resolve(plan.id.representative(), construct.initializer());
    FunctionPlan initializer =
        callables.values().stream()
            .filter(value -> value.id.representative().equals(initializerId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("constructor target is absent"));
    return new ExpressionNodes.Construct(
        info,
        initializer.target,
        lowerRuntimeType(construct.runtimeType(), plan),
        lowerArguments(construct.arguments(), plan),
        parameterIndices(construct.arguments()),
        initializer.declaration.parameters().size());
  }

  private ExpressionNode lowerBinary(CoreExpression.Binary binary, FunctionPlan plan) {
    ExpressionNode left = lowerExpression(binary.left(), plan);
    ExpressionNode right = lowerExpression(binary.right(), plan);
    return switch (binary.operator()) {
      case ADD -> new ExpressionNodes.Add(left, right);
      case STRING_CONCAT -> new ExpressionNodes.StringConcat(left, right);
      case SUBTRACT -> new ExpressionNodes.Subtract(left, right);
      case MULTIPLY -> new ExpressionNodes.Multiply(left, right);
      case DIVIDE -> new ExpressionNodes.Divide(left, right);
      case REMAINDER -> new ExpressionNodes.Remainder(left, right);
      case LESS -> new ExpressionNodes.Less(left, right);
      case LESS_EQUAL -> new ExpressionNodes.LessEqual(left, right);
      case GREATER -> new ExpressionNodes.Greater(left, right);
      case GREATER_EQUAL -> new ExpressionNodes.GreaterEqual(left, right);
      case EQUAL -> new ExpressionNodes.Equal(left, right);
      case NOT_EQUAL -> new ExpressionNodes.NotEqual(left, right);
      case AND -> new ExpressionNodes.And(left, right);
      case OR -> new ExpressionNodes.Or(left, right);
      case COALESCE -> new ExpressionNodes.Coalesce(left, right);
    };
  }

  private ExpressionNode lowerCall(CoreExpression.Call call, FunctionPlan plan) {
    DefinitionOccurrenceId targetId = resolve(plan, call.nodeIndex(), call.target());
    FunctionPlan target = callables.get(targetId);
    if (target == null && !call.virtual())
      throw new IllegalStateException("core call target is absent: " + targetId);
    if (call.receiver().isPresent()) {
      if (target != null
          && artifact.authoring().occurrence(targetId).orElseThrow().role()
              == dev.w0fv1.norm.core.CoreDefinitionRole.CONSTRUCTOR) {
        return new ExpressionNodes.ConstructorCall(
            target.target,
            lowerExpression(call.receiver().orElseThrow(), plan),
            lowerArguments(call.arguments(), plan),
            parameterIndices(call.arguments()),
            java.util.stream.Stream.concat(
                    call.receiverTypeArguments().stream(), call.reifiedArguments().stream())
                .map(value -> lowerRuntimeType(value, plan))
                .toArray(ExpressionNode[]::new),
            target.declaration.parameters().size());
      }
      if (call.virtual()) {
        return new ExpressionNodes.DispatchedCall(
            identity.slot(targetId),
            lowerExpression(call.receiver().orElseThrow(), plan),
            lowerArguments(call.arguments(), plan),
            parameterIndices(call.arguments()),
            call.reifiedArguments().stream()
                .map(value -> lowerRuntimeType(value, plan))
                .toArray(ExpressionNode[]::new),
            call.nullSafe(),
            builtinDispatch);
      }
      return new ExpressionNodes.MethodCall(
          target.target,
          lowerExpression(call.receiver().orElseThrow(), plan),
          lowerArguments(call.arguments(), plan),
          parameterIndices(call.arguments()),
          call.reifiedArguments().stream()
              .map(value -> lowerRuntimeType(value, plan))
              .toArray(ExpressionNode[]::new),
          call.receiverTypeArguments().stream()
              .map(value -> lowerRuntimeType(value, plan))
              .toArray(ExpressionNode[]::new),
          call.nullSafe());
    }
    return new ExpressionNodes.FunctionCall(
        target.target,
        lowerArguments(call.arguments(), plan),
        parameterIndices(call.arguments()),
        call.reifiedArguments().stream()
            .map(value -> lowerRuntimeType(value, plan))
            .toArray(ExpressionNode[]::new));
  }

  private ExpressionNode lowerClosure(CoreExpression.Closure closure, FunctionPlan plan) {
    DefinitionOccurrenceId targetId = resolve(plan, closure.nodeIndex(), closure.target());
    FunctionPlan target = callables.get(targetId);
    if (target == null && !closure.virtual())
      throw new IllegalStateException("core closure target is absent: " + targetId);
    return new ExpressionNodes.Closure(
        target == null ? null : target.target,
        targetId,
        closure.virtual() ? identity.slot(targetId) : null,
        (closure.virtual() || target.declaration.hasReceiver()) && closure.receiver().isEmpty(),
        closure.receiver().map(value -> lowerExpression(value, plan)).orElse(null),
        closure.captures().stream()
            .map(value -> lowerExpression(value, plan))
            .toArray(ExpressionNode[]::new),
        closure.reifiedArguments().stream()
            .map(value -> lowerRuntimeType(value, plan))
            .toArray(ExpressionNode[]::new),
        closure.receiverTypeArguments().stream()
            .map(value -> lowerRuntimeType(value, plan))
            .toArray(ExpressionNode[]::new),
        lowerRuntimeType(
            new CoreRuntimeType(
                closure.type(),
                java.util.stream.IntStream.range(0, plan.declaration.reifiedTypeLocals().size())
                    .mapToObj(
                        index ->
                            new CoreTypeCapture(
                                index, plan.declaration.reifiedTypeLocals().get(index)))
                    .toList()),
            plan),
        annotations);
  }

  private ExpressionNode lowerInterfaceCall(CoreExpression.InterfaceCall call, FunctionPlan plan) {
    DefinitionOccurrenceId requirement = resolve(plan, call.nodeIndex(), call.requirement());
    return new ExpressionNodes.DispatchedCall(
        identity.slot(requirement),
        lowerExpression(call.receiver(), plan),
        lowerArguments(call.arguments(), plan),
        parameterIndices(call.arguments()),
        call.reifiedArguments().stream()
            .map(value -> lowerRuntimeType(value, plan))
            .toArray(ExpressionNode[]::new),
        call.nullSafe(),
        builtinDispatch);
  }

  private DefinitionOccurrenceId resolve(
      FunctionPlan plan, int nodeIndex, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalStateException("pending definition reached the backend");
    }
    DefinitionOccurrenceId target = artifact.authoring().target(plan.id, nodeIndex);
    DefinitionId resolved = program.resolve(plan.id.representative(), reference);
    if (!artifact
        .authoring()
        .occurrence(target)
        .orElseThrow()
        .representedDefinitions()
        .contains(resolved)) {
      throw new IllegalStateException("authoring target does not represent the core reference");
    }
    return target;
  }

  private DefinitionId resolve(DefinitionId owner, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalStateException("pending definition reached the backend");
    }
    return program.resolve(owner, reference);
  }

  private ExpressionNode[] lowerArguments(List<CoreArgument> arguments, FunctionPlan plan) {
    return arguments.stream()
        .map(argument -> lowerExpression(argument.value(), plan))
        .toArray(ExpressionNode[]::new);
  }

  private static int[] parameterIndices(List<CoreArgument> arguments) {
    return arguments.stream().mapToInt(CoreArgument::parameterIndex).toArray();
  }

  private ExpressionNode lowerRuntimeType(CoreRuntimeType type, FunctionPlan plan) {
    return new ExpressionNodes.TypeDescriptor(
        identity.type(plan.id.representative(), type.template()),
        type.captures().stream().mapToInt(CoreTypeCapture::typeParameterIndex).toArray(),
        type.captures().stream()
            .map(capture -> plan.binding(capture.localIndex()))
            .toArray(FrameBinding[]::new));
  }

  private SourceSection section(DefinitionOccurrenceId occurrence, int nodeIndex) {
    return artifact.authoring().span(occurrence, nodeIndex).map(this::section).orElse(null);
  }

  private SourceSection section(SourceSpan span) {
    Source source =
        sources.computeIfAbsent(
            span.source().id(),
            ignored ->
                Source.newBuilder(
                        LanguageMetadata.ID, span.source().text(), span.source().displayName())
                    .uri(span.source().id().uri())
                    .build());
    return source.createSection(span.startOffset(), span.length());
  }

  private static FrameSlotKind slotKind(CoreType type) {
    if (type.equals(CoreType.DYNAMIC) || type.isNullable()) return FrameSlotKind.Object;
    if (type instanceof CoreType.Declared declared) {
      if (!(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)) {
        return FrameSlotKind.Object;
      }
      return switch (builtin.id().value()) {
        case "std.core.Integer" -> FrameSlotKind.Int;
        case "std.core.Long" -> FrameSlotKind.Long;
        case "std.core.Float" -> FrameSlotKind.Float;
        case "std.core.Double" -> FrameSlotKind.Double;
        case "std.core.Boolean" -> FrameSlotKind.Boolean;
        default -> FrameSlotKind.Object;
      };
    }
    return FrameSlotKind.Object;
  }

  private CollectionElementNode lowerCollectionElement(
      CoreCollectionElement element, FunctionPlan plan) {
    return switch (element) {
      case CoreExpression expression ->
          new CollectionElementNode.Value(lowerExpression(expression, plan));
      case CoreCollectionElement.Conditional conditional ->
          new CollectionElementNode.Conditional(
              lowerExpression(conditional.condition(), plan),
              lowerCollectionElement(conditional.thenElement(), plan),
              conditional
                  .elseElement()
                  .map(value -> lowerCollectionElement(value, plan))
                  .orElse(null));
      case CoreCollectionElement.Repeated repeated -> {
        IterationNodes nodes = iterationNodes(repeated.iteration(), plan);
        yield new CollectionElementNode.Repeated(
            new IterationLoopNode<>(
                plan.binding(repeated.iteratorLocal()),
                plan.binding(repeated.variableLocal()),
                repeated.indexLocal().isPresent()
                    ? java.util.Optional.of(plan.binding(repeated.indexLocal().orElseThrow()))
                    : java.util.Optional.empty(),
                lowerExpression(repeated.iterable(), plan),
                new CollectionElementNode.Body(lowerCollectionElement(repeated.element(), plan)),
                nodes.factory(),
                nodes.cursor()));
      }
    };
  }

  private IterationNodes iterationNodes(CoreIteration iteration, FunctionPlan plan) {
    StatementNodes.IteratorFactoryNode factory;
    StatementNodes.IteratorCursorNode cursor;
    switch (iteration) {
      case CoreIteration.Builtin builtin -> {
        factory = new StatementNodes.BuiltinIteratorFactory(builtin.intrinsic());
        cursor = new StatementNodes.BuiltinIteratorCursor();
      }
      case CoreIteration.Interface protocol -> {
        factory =
            new StatementNodes.InterfaceIteratorFactory(
                identity.slot(resolve(plan.id.representative(), protocol.iteratorRequirement())),
                builtinDispatch);
        cursor =
            new StatementNodes.InterfaceIteratorCursor(
                identity.slot(resolve(plan.id.representative(), protocol.hasNextRequirement())),
                identity.slot(resolve(plan.id.representative(), protocol.nextRequirement())),
                builtinDispatch);
      }
    }
    return new IterationNodes(factory, cursor);
  }

  private record IterationNodes(
      StatementNodes.IteratorFactoryNode factory, StatementNodes.IteratorCursorNode cursor) {}

  private static final class FunctionPlan {
    private final DefinitionOccurrenceId id;
    private final CoreDefinition.Callable declaration;
    private final FrameDescriptor.Builder frame = FrameDescriptor.newBuilder();
    private final List<FrameBinding> arguments = new ArrayList<>();
    private final Map<Integer, FrameBinding> bindings = new LinkedHashMap<>();
    private FrameDescriptor descriptor;
    private FunctionRootNode root;
    private RootCallTarget implementation;
    private RootCallTarget target;
    private final dev.w0fv1.norm.core.CoreExecutionLayout layout;

    private FunctionPlan(
        DefinitionOccurrenceId id,
        CoreDefinition.Callable declaration,
        dev.w0fv1.norm.core.CoreExecutionLayout layout) {
      this.layout = layout;
      this.id = id;
      this.declaration = declaration;
    }

    private void allocate(CoreLocal local) {
      FrameSlotKind kind = slotKind(local.type());
      bindings.put(
          local.index(),
          new FrameBinding(
              frame.addSlot(kind, "$" + local.index(), null),
              kind,
              layout == null ? null : layout.local(local.index())));
    }

    private FrameBinding binding(int local) {
      FrameBinding binding = bindings.get(local);
      if (binding == null) throw new IllegalStateException("core local is absent: " + local);
      return binding;
    }
  }
}
