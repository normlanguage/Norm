package dev.w0fv1.norm.core;

import dev.w0fv1.norm.abi.ExceptionAbi;
import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.semantic.PatternCoverage;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.ValueCategory;
import dev.w0fv1.norm.value.LexicalLifetime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class CoreProgramVerifier {
  private final CoreProgram program;
  private final BuiltinCatalog builtins = BuiltinCatalog.standard();
  private final CoreInterfaceHierarchy interfaces;
  private final Deque<Control> controls = new ArrayDeque<>();
  private CoreReferenceFlow referenceFlow;
  private CoreFunctionInterceptorProtocol functionInterceptor;
  private CoreParameterInterceptorProtocol parameterInterceptor;
  private CoreFieldInterceptorProtocol fieldInterceptor;
  private DefinitionId resolvedExceptionDefinition;
  private boolean exceptionDefinitionResolved;

  private CoreProgramVerifier(CoreProgram program) {
    this.program = Objects.requireNonNull(program, "program");
    interfaces = new CoreInterfaceHierarchy(program);
  }

  static void verify(CoreProgram program) {
    new CoreProgramVerifier(program).verify();
  }

  private void verify() {
    indexExceptionDefinition();
    for (CoreDefinitionRecord record : program.definitions()) {
      if (record.definition() instanceof CoreDefinition.Interface declaration) {
        verifyInterface(record.id(), declaration);
      }
    }
    functionInterceptor = CoreFunctionInterceptorProtocol.resolve(program).orElse(null);
    parameterInterceptor = CoreParameterInterceptorProtocol.resolve(program).orElse(null);
    fieldInterceptor = CoreFieldInterceptorProtocol.resolve(program).orElse(null);
    for (CoreDefinitionRecord record : program.definitions()) {
      switch (record.definition()) {
        case CoreDefinition.Callable callable -> verifyCallable(record.id(), callable);
        case CoreDefinition.Aggregate declaration -> verifyAggregate(record.id(), declaration);
        case CoreDefinition.Enum declaration -> verifyEnum(record.id(), declaration);
        case CoreDefinition.Interface ignored -> {}
        case CoreDefinition.InterfaceMethod method -> verifyInterfaceMethod(record.id(), method);
        case CoreDefinition.BuiltinConformance conformance ->
            verifyBuiltinConformance(record.id(), conformance);
      }
    }
    verifyBuiltinDispatchUniqueness();
  }

  private void verifyBuiltinDispatchUniqueness() {
    Map<BuiltinTypeId, Set<DefinitionId>> indexed = new LinkedHashMap<>();
    Map<BuiltinTypeId, Map<DefinitionId, CoreType.Declared>> interfaces = new LinkedHashMap<>();
    for (CoreDefinitionRecord record : program.definitions()) {
      if (!(record.definition() instanceof CoreDefinition.BuiltinConformance conformance)) continue;
      CoreType.Declared concrete =
          (CoreType.Declared) absolute(record.id(), conformance.concreteBuiltinType());
      BuiltinTypeId builtin = ((CoreTypeConstructor.Builtin) concrete.constructor()).id();
      InterfaceInstance instance = interfaceInstance(record.id(), conformance.interfaceType());
      CoreType.Declared previous =
          interfaces
              .computeIfAbsent(builtin, ignored -> new LinkedHashMap<>())
              .putIfAbsent(instance.definition(), instance.type());
      if (previous != null && !previous.equals(instance.type())) {
        throw new IllegalArgumentException("builtin interface instantiations conflict");
      }
      Set<DefinitionId> requirements = indexed.computeIfAbsent(builtin, ignored -> new HashSet<>());
      for (CoreWitness witness : conformance.witnesses()) {
        if (!requirements.add(resolve(record.id(), witness.requirement()))) {
          throw new IllegalArgumentException("builtin interface dispatch must be unique");
        }
      }
    }
  }

  private void verifyCallable(DefinitionId id, CoreDefinition.Callable callable) {
    int parameterCount = callable.reifiedTypeLocals().size();
    verifyTypeParameters(id, callable.typeParameters(), parameterCount);
    callable.receiverType().ifPresent(type -> verifyStoredType(id, type, parameterCount));
    callable.captureTypes().forEach(type -> verifyStoredType(id, type, parameterCount));
    callable.parameterTypes().forEach(type -> verifyParameterType(id, type, parameterCount));
    if (callable.parameters().stream().map(CoreCallableParameter::name).distinct().count()
        != callable.parameters().size()) {
      throw new IllegalArgumentException("callable parameter names must be unique");
    }
    Set<DefinitionId> interceptorTypes = new HashSet<>();
    for (CoreInterceptor interceptor : callable.interceptors()) {
      if (!interceptorTypes.add(resolve(id, interceptor.annotation()))) {
        throw new IllegalArgumentException("interceptor annotation must be unique per callable");
      }
    }
    callable
        .interceptors()
        .forEach(
            interceptor ->
                CoreAnnotationVerifier.verifyInterceptor(
                    program, id, interceptor, functionInterceptor));
    for (CoreCallableParameter parameter : callable.parameters()) {
      Set<DefinitionId> parameterInterceptorTypes = new HashSet<>();
      for (CoreInterceptor interceptor : parameter.interceptors()) {
        if (!parameterInterceptorTypes.add(resolve(id, interceptor.annotation()))) {
          throw new IllegalArgumentException("interceptor annotation must be unique per parameter");
        }
        CoreAnnotationVerifier.verifyParameterInterceptor(
            program, id, parameter, interceptor, parameterInterceptor);
      }
    }
    verifyReturnType(id, callable.returnType(), parameterCount);
    callable.locals().forEach(local -> verifyLocalType(id, local, parameterCount));
    if (!controls.isEmpty()) throw new IllegalStateException("core control stack is not empty");
    referenceFlow = new CoreReferenceFlow();
    List<Integer> entryLocals = new ArrayList<>();
    if (callable.receiverType().isPresent()) entryLocals.add(0);
    entryLocals.addAll(callable.captureLocals());
    entryLocals.addAll(callable.parameterLocals());
    entryLocals.addAll(callable.reifiedTypeLocals());
    verifyBlock(id, callable, callable.body(), entryLocals, true);
    referenceFlow = null;
  }

  private void verifyAggregate(DefinitionId id, CoreDefinition.Aggregate declaration) {
    if (declaration.kind()
        == CoreAggregateKind.VALUE
        != (declaration.valueCategory() == CoreValueCategory.VALUE)) {
      throw new IllegalArgumentException("aggregate kind and value category disagree");
    }
    if (declaration.kind() == CoreAggregateKind.ANNOTATION) {
      if (!declaration.typeParameters().isEmpty() || declaration.parentType().isPresent()) {
        throw new IllegalArgumentException("annotation cannot be generic or inherit a class");
      }
      CoreAnnotationPolicy.resolve(program, id, declaration);
    } else if (CoreAnnotationPolicy.usesPolicyInterfaces(program, id, declaration)) {
      throw new IllegalArgumentException(
          "annotation policy interfaces require an annotation aggregate");
    }
    int inheritedFields = 0;
    DefinitionId parentId = null;
    CoreType.Declared parentInstance = null;
    if (declaration.parentType().isPresent()) {
      CoreType parentType = declaration.parentType().orElseThrow();
      verifyValueType(id, parentType, declaration.typeParameters().size());
      CoreType absoluteParent = absolute(id, parentType);
      if (!(absoluteParent instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)
          || !(program.definition(resolveExternal(user.definition())).orElseThrow()
              instanceof CoreDefinition.Aggregate parent)
          || declaration.kind() != CoreAggregateKind.CLASS
          || parent.kind() != CoreAggregateKind.CLASS
          || parent.valueCategory() != CoreValueCategory.IDENTITY
          || declaration.valueCategory() != CoreValueCategory.IDENTITY) {
        throw new IllegalArgumentException("aggregate inheritance requires class types");
      }
      parentInstance = declared;
      parentId = resolveExternal(user.definition());
      inheritedFields = parent.fieldCount();
      requireAcyclicAggregate(id, parentId, new HashSet<>());
    }
    if (declaration.fieldCount() != inheritedFields + declaration.fields().size()) {
      throw new IllegalArgumentException("aggregate field count does not match its parent");
    }
    if (declaration.fields().stream().map(CoreField::name).distinct().count()
        != declaration.fields().size()) {
      throw new IllegalArgumentException("aggregate field names must be unique");
    }
    for (int index = 0; index < declaration.fields().size(); index++) {
      CoreField field = declaration.fields().get(index);
      if (field.ordinal() != inheritedFields + index) {
        throw new IllegalArgumentException("aggregate field ordinals must be contiguous");
      }
      verifyStoredType(id, field.type(), declaration.typeParameters().size());
      Set<DefinitionId> interceptors = new HashSet<>();
      for (CoreInterceptor interceptor : field.interceptors()) {
        DefinitionId annotation = resolve(id, interceptor.annotation());
        if (!interceptors.add(annotation)) {
          throw new IllegalArgumentException("field interceptors must be unique");
        }
        CoreAnnotationVerifier.verifyFieldInterceptor(
            program, id, field, interceptor, fieldInterceptor);
      }
    }
    verifyTypeParameters(id, declaration.typeParameters(), declaration.typeParameters().size());
    if (resolvedExceptionDefinition != null
        && !declaration.typeParameters().isEmpty()
        && aggregateView(aggregateType(id, declaration), resolvedExceptionDefinition) != null) {
      throw new IllegalArgumentException("Exception descendants cannot be generic");
    }
    Set<DefinitionId> constructors = new HashSet<>();
    for (CoreDefinitionLink constructorReference : declaration.constructors()) {
      DefinitionId constructorId = resolve(id, constructorReference);
      CoreDefinition constructor = program.definition(constructorId).orElseThrow();
      if (!constructors.add(constructorId)
          || !(constructor instanceof CoreDefinition.Callable constructorCallable)
          || constructorCallable.receiverType().isEmpty()
          || !constructorCallable.typeParameters().isEmpty()
          || !constructorCallable.returnType().equals(CoreType.VOID)
          || !isReceiverOf(
              constructorId,
              constructorCallable.receiverType().orElseThrow(),
              id,
              declaration.typeParameters().size())) {
        throw new IllegalArgumentException("aggregate constructor must target a distinct method");
      }
    }
    if (declaration.kind() == CoreAggregateKind.ANNOTATION) {
      CoreAnnotationVerifier.verifyDeclaration(program, id, declaration);
    }
    verifyAggregateDispatch(id, declaration, parentId, parentInstance);
    Set<DefinitionId> interfaces = new HashSet<>();
    Map<DefinitionId, CoreType.Declared> inheritedInterfaces = new LinkedHashMap<>();
    for (CoreConformance conformance : declaration.conformances()) {
      verifyValueType(id, conformance.interfaceType(), declaration.typeParameters().size());
      InterfaceInstance instance = interfaceInstance(id, conformance.interfaceType());
      if (!interfaces.add(instance.definition())) {
        throw new IllegalArgumentException("aggregate conformances must be unique");
      }
      collectInterfaceInstances(instance, inheritedInterfaces);
      verifyConformance(id, declaration, instance, conformance);
    }
  }

  private void verifyAggregateDispatch(
      DefinitionId id,
      CoreDefinition.Aggregate declaration,
      DefinitionId parentId,
      CoreType.Declared parentInstance) {
    CoreType.Declared self = aggregateType(id, declaration);
    Map<DefinitionId, CoreMethodDispatch> dispatchBySlot = new LinkedHashMap<>();
    for (CoreMethodDispatch dispatch : declaration.dispatch()) {
      DefinitionId slotId = resolve(id, dispatch.slot());
      if (dispatchBySlot.putIfAbsent(slotId, dispatch) != null) {
        throw new IllegalArgumentException("aggregate method dispatch slots must be unique");
      }
      DefinitionId implementationId = resolve(id, dispatch.implementation());
      CoreDefinition.Callable slot = dispatchMethod(slotId, "dispatch slot");
      CoreDefinition.Callable implementation =
          dispatchMethod(implementationId, "dispatch implementation");
      verifyValueType(id, dispatch.receiverType(), declaration.typeParameters().size());
      CoreType receiverType = absolute(id, dispatch.receiverType());
      DefinitionId implementationOwner = methodOwner(implementationId, implementation);
      CoreType.Declared implementationView = aggregateView(self, implementationOwner);
      if (implementationView == null || !implementationView.equals(receiverType)) {
        throw new IllegalArgumentException(
            "aggregate dispatch receiver does not match its implementation owner");
      }
      CoreType.Declared slotView = aggregateView(self, methodOwner(slotId, slot));
      if (slotView == null) {
        throw new IllegalArgumentException("aggregate dispatch slot is not inherited");
      }
      verifyDispatchMethodAbi(
          slotId,
          slot,
          slotView,
          implementationId,
          implementation,
          implementationView,
          declaration.typeParameters().size());
    }

    Map<DefinitionId, CoreMethodDispatch> inherited = new LinkedHashMap<>();
    if (parentId != null) {
      Map<DefinitionId, DefinitionId> replacements = new LinkedHashMap<>();
      CoreDefinition.Aggregate parent =
          (CoreDefinition.Aggregate) program.definition(parentId).orElseThrow();
      for (CoreMethodDispatch parentDispatch : parent.dispatch()) {
        DefinitionId slotId = resolve(parentId, parentDispatch.slot());
        inherited.put(slotId, parentDispatch);
        CoreMethodDispatch actual = dispatchBySlot.get(slotId);
        if (actual == null) {
          throw new IllegalArgumentException("aggregate dispatch omits an inherited slot");
        }
        DefinitionId parentImplementation = resolve(parentId, parentDispatch.implementation());
        DefinitionId actualImplementation = resolve(id, actual.implementation());
        DefinitionId previous =
            replacements.putIfAbsent(parentImplementation, actualImplementation);
        if (previous != null && !previous.equals(actualImplementation)) {
          throw new IllegalArgumentException(
              "aggregate dispatch must replace an inherited implementation consistently");
        }
        if (actualImplementation.equals(parentImplementation)) {
          CoreType expectedReceiver =
              absolute(parentId, parentDispatch.receiverType())
                  .substitute(parentInstance.arguments()::get);
          requireSameType(
              expectedReceiver, absolute(id, actual.receiverType()), "inherited dispatch receiver");
        } else {
          if (!methodOwner(
                  actualImplementation,
                  dispatchMethod(actualImplementation, "dispatch implementation"))
              .equals(id)) {
            throw new IllegalArgumentException(
                "aggregate dispatch override must be declared by the child");
          }
          CoreMethodDispatch selfSlot = dispatchBySlot.get(actualImplementation);
          if (selfSlot == null
              || !resolve(id, selfSlot.implementation()).equals(actualImplementation)) {
            throw new IllegalArgumentException(
                "aggregate dispatch override must declare its own slot");
          }
        }
      }
    }
    for (Map.Entry<DefinitionId, CoreMethodDispatch> entry : dispatchBySlot.entrySet()) {
      if (inherited.containsKey(entry.getKey())) continue;
      DefinitionId implementation = resolve(id, entry.getValue().implementation());
      if (!methodOwner(entry.getKey(), dispatchMethod(entry.getKey(), "dispatch slot")).equals(id)
          || !implementation.equals(entry.getKey())
          || !absolute(id, entry.getValue().receiverType()).equals(self)) {
        throw new IllegalArgumentException(
            "new aggregate dispatch slots must be declared by the aggregate");
      }
    }
  }

  private CoreDefinition.Callable dispatchMethod(DefinitionId id, String subject) {
    CoreDefinition definition = program.definition(id).orElseThrow();
    if (!(definition instanceof CoreDefinition.Callable callable)
        || callable.receiverType().isEmpty()
        || !callable.captureTypes().isEmpty()
        || isAggregateConstructor(id)) {
      throw new IllegalArgumentException(subject + " must be a non-capturing method");
    }
    return callable;
  }

  private boolean isAggregateConstructor(DefinitionId id) {
    for (CoreDefinitionRecord record : program.definitions()) {
      if (record.definition() instanceof CoreDefinition.Aggregate aggregate
          && aggregate.constructors().stream()
              .map(constructor -> resolve(record.id(), constructor))
              .anyMatch(id::equals)) {
        return true;
      }
    }
    return false;
  }

  private DefinitionId methodOwner(DefinitionId id, CoreDefinition.Callable method) {
    CoreType receiver = nonNullable(absolute(id, method.receiverType().orElseThrow()));
    if (!(receiver instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("dispatch method receiver must be an aggregate type");
    }
    DefinitionId owner = resolveExternal(user.definition());
    if (!(program.definition(owner).orElseThrow() instanceof CoreDefinition.Aggregate)) {
      throw new IllegalArgumentException("dispatch method receiver must be an aggregate type");
    }
    return owner;
  }

  private void verifyDispatchMethodAbi(
      DefinitionId slotId,
      CoreDefinition.Callable slot,
      CoreType.Declared slotView,
      DefinitionId implementationId,
      CoreDefinition.Callable implementation,
      CoreType.Declared implementationView,
      int aggregateParameterCount) {
    if (slot.typeParameters().size() != implementation.typeParameters().size()
        || slot.parameterTypes().size() != implementation.parameterTypes().size()) {
      throw new IllegalArgumentException("dispatch implementation does not match its slot ABI");
    }
    for (int index = 0; index < slot.typeParameters().size(); index++) {
      Optional<CoreType> expected =
          normalizedMethodBound(
              slotId, slot, slotView, slot.typeParameters().get(index), aggregateParameterCount);
      Optional<CoreType> actual =
          normalizedMethodBound(
              implementationId,
              implementation,
              implementationView,
              implementation.typeParameters().get(index),
              aggregateParameterCount);
      if (!expected.equals(actual)) {
        throw new IllegalArgumentException("dispatch implementation does not match its slot ABI");
      }
    }
    for (int index = 0; index < slot.parameterTypes().size(); index++) {
      requireSameType(
          normalizeMethodType(
              slotId, slot.parameterTypes().get(index), slot, slotView, aggregateParameterCount),
          normalizeMethodType(
              implementationId,
              implementation.parameterTypes().get(index),
              implementation,
              implementationView,
              aggregateParameterCount),
          "dispatch parameter");
    }
    CoreType expectedResult =
        normalizeMethodType(slotId, slot.returnType(), slot, slotView, aggregateParameterCount);
    CoreType actualResult =
        normalizeMethodType(
            implementationId,
            implementation.returnType(),
            implementation,
            implementationView,
            aggregateParameterCount);
    requireAssignable(expectedResult, actualResult, "dispatch result");
  }

  private Optional<CoreType> normalizedMethodBound(
      DefinitionId owner,
      CoreDefinition.Callable method,
      CoreType.Declared receiver,
      CoreTypeParameter parameter,
      int aggregateParameterCount) {
    return parameter
        .upperBound()
        .map(bound -> normalizeMethodType(owner, bound, method, receiver, aggregateParameterCount));
  }

  private CoreType normalizeMethodType(
      DefinitionId owner,
      CoreType type,
      CoreDefinition.Callable method,
      CoreType.Declared receiver,
      int aggregateParameterCount) {
    int receiverParameters = method.receiverTypeParameterCount();
    return absolute(owner, type)
        .substitute(
            index -> {
              if (index < receiverParameters) return receiver.arguments().get(index);
              return new CoreType.Parameter(
                  aggregateParameterCount + index - receiverParameters, CoreNullability.NON_NULL);
            });
  }

  private static CoreType.Declared aggregateType(
      DefinitionId id, CoreDefinition.Aggregate declaration) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(id)),
        java.util.stream.IntStream.range(0, declaration.typeParameters().size())
            .mapToObj(index -> new CoreType.Parameter(index, CoreNullability.NON_NULL))
            .map(CoreType.class::cast)
            .toList(),
        declaration.valueCategory(),
        CoreNullability.NON_NULL);
  }

  private void verifyInterface(DefinitionId id, CoreDefinition.Interface declaration) {
    verifyTypeParameters(id, declaration.typeParameters(), declaration.typeParameters().size());
    Set<DefinitionId> parents = new HashSet<>();
    Map<DefinitionId, CoreType.Declared> inherited = new LinkedHashMap<>();
    for (CoreType parent : declaration.directParents()) {
      verifyValueType(id, parent, declaration.typeParameters().size());
      InterfaceInstance instance = interfaceInstance(id, parent);
      if (!parents.add(instance.definition())) {
        throw new IllegalArgumentException("direct interface parents must be unique");
      }
      collectInterfaceInstances(instance, inherited);
      requireAcyclicInterface(id, instance.definition(), new HashSet<>());
    }
    Set<DefinitionId> methods = new HashSet<>();
    for (CoreDefinitionLink link : declaration.declaredMethods()) {
      DefinitionId methodId = resolve(id, link);
      if (!methods.add(methodId)) {
        throw new IllegalArgumentException("declared interface methods must be unique");
      }
      CoreDefinition target = program.definition(methodId).orElseThrow();
      if (!(target instanceof CoreDefinition.InterfaceMethod method)
          || !isReceiverOf(
              methodId, method.receiverInterfaceType(), id, declaration.typeParameters().size())) {
        throw new IllegalArgumentException("declared interface method has the wrong receiver ABI");
      }
    }
  }

  private void collectInterfaceInstances(
      InterfaceInstance instance, Map<DefinitionId, CoreType.Declared> result) {
    interfaces.collect(instance.definition(), instance.type(), result);
  }

  private void verifyInterfaceMethod(DefinitionId id, CoreDefinition.InterfaceMethod method) {
    InterfaceInstance receiver = interfaceInstance(id, method.receiverInterfaceType());
    int receiverParameterCount = receiver.declaration().typeParameters().size();
    int parameterCount = receiverParameterCount + method.typeParameters().size();
    if (!isReceiverOf(
        id, method.receiverInterfaceType(), receiver.definition(), receiverParameterCount)) {
      throw new IllegalArgumentException(
          "interface method receiver must expose its type parameters");
    }
    verifyTypeParameters(id, method.typeParameters(), parameterCount);
    method.parameterTypes().forEach(type -> verifyParameterType(id, type, parameterCount));
    verifyReturnType(id, method.returnType(), parameterCount);
  }

  private void verifyConformance(
      DefinitionId aggregateId,
      CoreDefinition.Aggregate declaration,
      InterfaceInstance instance,
      CoreConformance conformance) {
    Map<DefinitionId, CoreDefinition.InterfaceMethod> requirements =
        inheritedRequirements(instance.definition());
    Map<DefinitionId, CoreWitnessTarget> witnesses = new LinkedHashMap<>();
    for (CoreWitness witness : conformance.witnesses()) {
      DefinitionId requirementId = resolve(aggregateId, witness.requirement());
      if (witnesses.putIfAbsent(requirementId, witness.implementation()) != null) {
        throw new IllegalArgumentException("conformance witnesses must be unique");
      }
    }
    if (!witnesses.keySet().equals(requirements.keySet())) {
      throw new IllegalArgumentException("conformance witnesses must be complete");
    }
    for (Map.Entry<DefinitionId, CoreDefinition.InterfaceMethod> entry : requirements.entrySet()) {
      DefinitionId requirementId = entry.getKey();
      CoreDefinition.InterfaceMethod requirement = entry.getValue();
      CoreWitnessTarget witness = witnesses.get(requirementId);
      if (!(witness instanceof CoreWitnessTarget.Callable callableWitness)) {
        throw new IllegalArgumentException("source aggregate witnesses must target callables");
      }
      DefinitionId implementationId = resolve(aggregateId, callableWitness.definition());
      CoreDefinition implementationDefinition = program.definition(implementationId).orElseThrow();
      boolean aggregateReceiver =
          implementationDefinition instanceof CoreDefinition.Callable candidate
              && candidate.receiverType().isPresent()
              && isReceiverOf(
                  implementationId,
                  candidate.receiverType().orElseThrow(),
                  aggregateId,
                  declaration.typeParameters().size());
      boolean defaultReceiver =
          implementationDefinition instanceof CoreDefinition.Callable candidate
              && candidate.receiverType().isPresent()
              && absolute(implementationId, candidate.receiverType().orElseThrow())
                  instanceof CoreType.Declared receiver
              && receiver.constructor() instanceof CoreTypeConstructor.User user
              && interfaceSubstitutions(instance, resolveExternal(user.definition())) != null;
      int expectedReified =
          aggregateReceiver
              ? declaration.typeParameters().size() + requirement.typeParameters().size()
              : implementationDefinition instanceof CoreDefinition.Callable candidate
                  ? candidate.receiverTypeParameterCount() + candidate.typeParameters().size()
                  : -1;
      if (!(implementationDefinition instanceof CoreDefinition.Callable implementation)
          || implementation.receiverType().isEmpty()
          || implementation.reifiedTypeLocals().size() != expectedReified
          || (!aggregateReceiver && !defaultReceiver)) {
        throw new IllegalArgumentException(
            "conformance witness implementation has an incompatible receiver");
      }
      InterfaceInstance requirementOwner =
          interfaceInstance(requirementId, requirement.receiverInterfaceType());
      List<CoreType> substitutions =
          interfaceSubstitutions(instance, requirementOwner.definition());
      if (substitutions != null) {
        substitutions = new ArrayList<>(substitutions);
        for (int index = 0; index < requirement.typeParameters().size(); index++) {
          substitutions.add(
              new CoreType.Parameter(
                  declaration.typeParameters().size() + index, CoreNullability.NON_NULL));
        }
      }
      List<CoreType> implementationSubstitutions = new ArrayList<>();
      if (aggregateReceiver) {
        for (int index = 0; index < declaration.typeParameters().size(); index++) {
          implementationSubstitutions.add(new CoreType.Parameter(index, CoreNullability.NON_NULL));
        }
      } else {
        InterfaceInstance implementationOwner =
            interfaceInstance(implementationId, implementation.receiverType().orElseThrow());
        List<CoreType> receiverSubstitutions =
            interfaceSubstitutions(instance, implementationOwner.definition());
        if (receiverSubstitutions != null) {
          implementationSubstitutions.addAll(receiverSubstitutions);
        }
      }
      for (int index = 0; index < requirement.typeParameters().size(); index++) {
        implementationSubstitutions.add(
            new CoreType.Parameter(
                declaration.typeParameters().size() + index, CoreNullability.NON_NULL));
      }
      if (substitutions == null
          || implementationSubstitutions.size() != implementation.reifiedTypeLocals().size()
          || implementation.parameterTypes().size() != requirement.parameterTypes().size()) {
        throw new IllegalArgumentException(
            "conformance witness ABI does not match its requirement");
      }
      for (int parameter = 0; parameter < requirement.parameterTypes().size(); parameter++) {
        CoreType expected =
            absolute(requirementId, requirement.parameterTypes().get(parameter))
                .substitute(substitutions::get);
        requireSameType(
            expected,
            absolute(implementationId, implementation.parameterTypes().get(parameter))
                .substitute(implementationSubstitutions::get),
            "conformance witness parameter");
      }
      CoreType expectedReturn =
          absolute(requirementId, requirement.returnType()).substitute(substitutions::get);
      requireAssignable(
          expectedReturn,
          absolute(implementationId, implementation.returnType())
              .substitute(implementationSubstitutions::get),
          "conformance witness return");
      for (int index = 0; index < requirement.typeParameters().size(); index++) {
        CoreTypeParameter requiredParameter = requirement.typeParameters().get(index);
        CoreTypeParameter implementationParameter = implementation.typeParameters().get(index);
        if (requiredParameter.upperBound().isPresent()
            != implementationParameter.upperBound().isPresent()) {
          throw new IllegalArgumentException("conformance witness generic bound does not match");
        }
        if (requiredParameter.defaultType().isPresent()
            != implementationParameter.defaultType().isPresent()) {
          throw new IllegalArgumentException("conformance witness generic default does not match");
        }
        if (requiredParameter.upperBound().isPresent()) {
          CoreType expectedBound =
              absolute(requirementId, requiredParameter.upperBound().orElseThrow())
                  .substitute(substitutions::get);
          requireSameType(
              expectedBound,
              absolute(implementationId, implementationParameter.upperBound().orElseThrow())
                  .substitute(implementationSubstitutions::get),
              "conformance witness generic bound");
        }
        if (requiredParameter.defaultType().isPresent()) {
          CoreType expectedDefault =
              absolute(requirementId, requiredParameter.defaultType().orElseThrow())
                  .substitute(substitutions::get);
          requireSameType(
              expectedDefault,
              absolute(implementationId, implementationParameter.defaultType().orElseThrow())
                  .substitute(implementationSubstitutions::get),
              "conformance witness generic default");
        }
      }
    }
  }

  private void verifyBuiltinConformance(
      DefinitionId owner, CoreDefinition.BuiltinConformance conformance) {
    int parameterCount = conformance.typeParameters().size();
    verifyTypeParameters(owner, conformance.typeParameters(), parameterCount);
    verifyValueType(owner, conformance.concreteBuiltinType(), parameterCount);
    CoreType concrete = absolute(owner, conformance.concreteBuiltinType());
    if (!(concrete instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin)) {
      throw new IllegalArgumentException("builtin conformance requires a builtin concrete type");
    }
    verifyValueType(owner, conformance.interfaceType(), parameterCount);
    InterfaceInstance instance = interfaceInstance(owner, conformance.interfaceType());
    Map<DefinitionId, CoreDefinition.InterfaceMethod> requirements =
        inheritedRequirements(instance.definition());
    Map<DefinitionId, CoreWitnessTarget> witnesses = new LinkedHashMap<>();
    for (CoreWitness witness : conformance.witnesses()) {
      DefinitionId requirement = resolve(owner, witness.requirement());
      if (witnesses.putIfAbsent(requirement, witness.implementation()) != null) {
        throw new IllegalArgumentException("builtin conformance witnesses must be unique");
      }
    }
    if (!witnesses.keySet().equals(requirements.keySet())) {
      throw new IllegalArgumentException("builtin conformance witnesses must be complete");
    }
    for (Map.Entry<DefinitionId, CoreDefinition.InterfaceMethod> entry : requirements.entrySet()) {
      CoreWitnessTarget witness = witnesses.get(entry.getKey());
      boolean valid =
          switch (witness) {
            case CoreWitnessTarget.Intrinsic intrinsic ->
                matchesIntrinsicWitness(
                    owner,
                    concrete,
                    instance,
                    entry.getKey(),
                    entry.getValue(),
                    intrinsic.intrinsic());
            case CoreWitnessTarget.Callable callable ->
                matchesDefaultWitness(
                    owner, conformance, instance, entry.getKey(), entry.getValue(), callable);
          };
      if (!valid) {
        throw new IllegalArgumentException(
            "builtin conformance witness ABI does not match: "
                + concrete
                + " -> "
                + conformance.interfaceType()
                + " via "
                + witness);
      }
    }
  }

  private boolean matchesDefaultWitness(
      DefinitionId owner,
      CoreDefinition.BuiltinConformance conformance,
      InterfaceInstance instance,
      DefinitionId requirementId,
      CoreDefinition.InterfaceMethod requirement,
      CoreWitnessTarget.Callable witness) {
    DefinitionId implementationId = resolve(owner, witness.definition());
    CoreDefinition definition = program.definition(implementationId).orElse(null);
    if (!(definition instanceof CoreDefinition.Callable implementation)
        || implementation.receiverType().isEmpty()
        || implementation.reifiedTypeLocals().size()
            != ((CoreType.Declared) absolute(requirementId, requirement.receiverInterfaceType()))
                    .arguments()
                    .size()
                + requirement.typeParameters().size()
        || !absolute(implementationId, implementation.receiverType().orElseThrow())
            .equals(absolute(requirementId, requirement.receiverInterfaceType()))) {
      return false;
    }
    if (implementation.parameterTypes().size() != requirement.parameterTypes().size()) {
      return false;
    }
    for (int index = 0; index < requirement.parameterTypes().size(); index++) {
      CoreType expected = absolute(requirementId, requirement.parameterTypes().get(index));
      if (!expected.equals(
          absolute(implementationId, implementation.parameterTypes().get(index)))) {
        return false;
      }
    }
    CoreType expectedReturn = absolute(requirementId, requirement.returnType());
    return expectedReturn.equals(absolute(implementationId, implementation.returnType()));
  }

  private boolean matchesIntrinsicWitness(
      DefinitionId owner,
      CoreType concrete,
      InterfaceInstance instance,
      DefinitionId requirementId,
      CoreDefinition.InterfaceMethod requirement,
      dev.w0fv1.norm.abi.IntrinsicId intrinsic) {
    if (!requirement.typeParameters().isEmpty()) return false;
    InterfaceInstance requirementOwner =
        interfaceInstance(requirementId, requirement.receiverInterfaceType());
    List<CoreType> interfaceArguments =
        interfaceSubstitutions(instance, requirementOwner.definition());
    if (interfaceArguments == null) return false;
    for (BuiltinCatalog.IntrinsicCandidate candidate : builtins.intrinsicCandidates(intrinsic)) {
      if (candidate.receiver().isEmpty()
          || candidate.runtimeType()
          || candidate.parameters().size() != requirement.parameterTypes().size()) {
        continue;
      }
      Map<String, CoreType> substitutions = new LinkedHashMap<>();
      if (!bindPattern(concrete, candidate.receiver().orElseThrow(), substitutions)) continue;
      boolean parametersMatch = true;
      for (int index = 0; index < candidate.parameters().size(); index++) {
        CoreType expected =
            absolute(requirementId, requirement.parameterTypes().get(index))
                .substitute(interfaceArguments::get);
        if (!matchesSemanticType(
            expected, candidate.parameters().get(index).type(), substitutions)) {
          parametersMatch = false;
          break;
        }
      }
      CoreType expectedReturn =
          absolute(requirementId, requirement.returnType()).substitute(interfaceArguments::get);
      if (parametersMatch
          && matchesSemanticType(expectedReturn, candidate.result(), substitutions)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesSemanticType(
      CoreType expected, SemanticType pattern, Map<String, CoreType> substitutions) {
    return switch (pattern.kind()) {
      case TYPE_PARAMETER -> {
        CoreType substituted = substitutions.get(pattern.identity());
        yield substituted != null
            && expected.equals(pattern.isNullable() ? substituted.asNullable() : substituted);
      }
      case VOID -> expected.equals(CoreType.VOID);
      case NULL -> expected.equals(CoreType.NULL);
      case ERROR -> true;
      case EXISTENTIAL -> true;
      case REFERENCE ->
          expected instanceof CoreType.Reference reference
              && matchesSemanticType(reference.target(), pattern.referenceTarget(), substitutions);
      case DECLARED -> {
        if (!(expected instanceof CoreType.Declared declared)
            || declared.arguments().size() != pattern.arguments().size()
            || declared.category() != category(pattern.category())
            || declared.isNullable() != pattern.isNullable()
            || !matchesSemanticConstructor(declared.constructor(), pattern.identity())) {
          yield false;
        }
        boolean matches = true;
        for (int index = 0; index < pattern.arguments().size(); index++) {
          if (!matchesSemanticType(
              declared.arguments().get(index), pattern.arguments().get(index), substitutions)) {
            matches = false;
            break;
          }
        }
        yield matches;
      }
    };
  }

  private boolean matchesSemanticConstructor(
      CoreTypeConstructor constructor, String semanticIdentity) {
    return switch (constructor) {
      case CoreTypeConstructor.Builtin builtin -> builtin.id().value().equals(semanticIdentity);
      case CoreTypeConstructor.User user -> {
        DefinitionId id = resolveExternal(user.definition());
        CoreDefinition definition = program.definition(id).orElseThrow();
        CoreNominalTypeKey nominal =
            switch (definition) {
              case CoreDefinition.Aggregate declaration -> declaration.nominalType();
              case CoreDefinition.Enum declaration -> declaration.nominalType();
              case CoreDefinition.Interface declaration -> declaration.nominalType();
              default -> null;
            };
        yield nominal != null
            && nominal.module().name().equals("std")
            && (nominal.packageName() + "." + nominal.name()).equals(semanticIdentity);
      }
    };
  }

  private Map<DefinitionId, CoreDefinition.InterfaceMethod> inheritedRequirements(
      DefinitionId interfaceId) {
    Map<DefinitionId, CoreDefinition.InterfaceMethod> result = new LinkedHashMap<>();
    collectRequirements(interfaceId, result, new HashSet<>());
    return Map.copyOf(result);
  }

  private void collectRequirements(
      DefinitionId interfaceId,
      Map<DefinitionId, CoreDefinition.InterfaceMethod> result,
      Set<DefinitionId> visited) {
    if (!visited.add(interfaceId)) return;
    CoreDefinition.Interface declaration =
        (CoreDefinition.Interface) program.definition(interfaceId).orElseThrow();
    for (CoreType parent : declaration.directParents()) {
      collectRequirements(interfaceInstance(interfaceId, parent).definition(), result, visited);
    }
    for (CoreDefinitionLink link : declaration.declaredMethods()) {
      DefinitionId methodId = resolve(interfaceId, link);
      result.put(
          methodId, (CoreDefinition.InterfaceMethod) program.definition(methodId).orElseThrow());
    }
  }

  private void requireAcyclicInterface(
      DefinitionId root, DefinitionId current, Set<DefinitionId> visited) {
    if (current.equals(root)) {
      throw new IllegalArgumentException("interface inheritance must be acyclic");
    }
    if (!visited.add(current)) return;
    CoreDefinition.Interface declaration =
        (CoreDefinition.Interface) program.definition(current).orElseThrow();
    for (CoreType parent : declaration.directParents()) {
      requireAcyclicInterface(root, interfaceInstance(current, parent).definition(), visited);
    }
  }

  private void requireAcyclicAggregate(
      DefinitionId root, DefinitionId current, Set<DefinitionId> visited) {
    if (current.equals(root)) {
      throw new IllegalArgumentException("aggregate inheritance must be acyclic");
    }
    if (!visited.add(current)) return;
    CoreDefinition definition = program.definition(current).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate aggregate)
        || aggregate.parentType().isEmpty()) return;
    CoreType parent = nonNullable(absolute(current, aggregate.parentType().orElseThrow()));
    CoreTypeConstructor.User user =
        (CoreTypeConstructor.User) ((CoreType.Declared) parent).constructor();
    requireAcyclicAggregate(root, resolveExternal(user.definition()), visited);
  }

  private boolean isReceiverOf(
      DefinitionId owner, CoreType type, DefinitionId nominal, int parameterCount) {
    CoreType actual = absolute(owner, type);
    if (actual.isNullable()
        || !(actual instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(nominal)
        || declared.arguments().size() != parameterCount) {
      return false;
    }
    for (int index = 0; index < parameterCount; index++) {
      if (!(declared.arguments().get(index) instanceof CoreType.Parameter parameter)
          || parameter.index() != index
          || parameter.nullability() != CoreNullability.NON_NULL) {
        return false;
      }
    }
    return true;
  }

  private InterfaceInstance interfaceInstance(DefinitionId owner, CoreType type) {
    CoreInterfaceHierarchy.Instance instance = interfaces.instance(owner, type);
    return new InterfaceInstance(instance.definition(), instance.declaration(), instance.type());
  }

  private List<CoreType> interfaceSubstitutions(InterfaceInstance instance, DefinitionId target) {
    CoreType.Declared targetInstance =
        interfaces.instances(instance.definition(), instance.type()).get(target);
    return targetInstance == null ? null : targetInstance.arguments();
  }

  private record InterfaceInstance(
      DefinitionId definition, CoreDefinition.Interface declaration, CoreType.Declared type) {}

  private void verifyEnum(DefinitionId id, CoreDefinition.Enum declaration) {
    verifyTypeParameters(id, declaration.typeParameters(), declaration.typeParameters().size());
    if (declaration.variants().stream().map(CoreEnumVariant::key).distinct().count()
        != declaration.variants().size()) {
      throw new IllegalArgumentException("enum variants must be unique");
    }
    declaration
        .variants()
        .forEach(
            variant -> {
              if (variant.fields().stream().map(CoreField::name).distinct().count()
                  != variant.fields().size()) {
                throw new IllegalArgumentException("enum field names must be unique");
              }
              for (int index = 0; index < variant.fields().size(); index++) {
                CoreField field = variant.fields().get(index);
                if (field.ordinal() != index || !field.interceptors().isEmpty()) {
                  throw new IllegalArgumentException("enum field structure is invalid");
                }
                verifyStoredType(id, field.type(), declaration.typeParameters().size());
              }
            });
  }

  private void verifyBlock(DefinitionId owner, CoreDefinition.Callable callable, CoreBlock block) {
    verifyBlock(owner, callable, block, List.of(), false);
  }

  private void verifyBlock(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CoreBlock block,
      List<Integer> implicitLocals,
      boolean externalReferences) {
    referenceFlow.push();
    for (int localIndex : implicitLocals) {
      CoreLocal local = local(callable, localIndex);
      referenceFlow.declare(localIndex);
      if (local.type() instanceof CoreType.Reference) {
        referenceFlow.update(
            localIndex,
            externalReferences ? LexicalLifetime.longLived() : LexicalLifetime.unusable());
      }
    }
    for (CoreStatement statement : block.statements()) {
      switch (statement) {
        case CoreStatement.LocalDeclaration local -> {
          CoreLocal target = local(callable, local.localIndex());
          if (target.kind() != CoreLocal.Kind.VARIABLE) {
            throw new IllegalArgumentException("local declaration must bind a variable local");
          }
          verifyExpression(owner, callable, local.initializer());
          requireAssignable(
              owner, target.type(), owner, local.initializer().type(), "local initializer");
          referenceFlow.declare(local.localIndex());
          if (target.type() instanceof CoreType.Reference) {
            updateReferenceLifetime(local.localIndex(), local.initializer());
          }
        }
        case CoreStatement.LocalAssignment assignment -> {
          CoreLocal target = local(callable, assignment.localIndex());
          if (target.kind() != CoreLocal.Kind.VARIABLE
              && target.kind() != CoreLocal.Kind.PARAMETER) {
            throw new IllegalArgumentException("local assignment target is not mutable storage");
          }
          referenceFlow.requireDeclared(assignment.localIndex());
          verifyExpression(owner, callable, assignment.value());
          requireAssignable(
              owner, target.type(), owner, assignment.value().type(), "local assignment");
          if (target.type() instanceof CoreType.Reference) {
            updateReferenceLifetime(assignment.localIndex(), assignment.value());
          }
        }
        case CoreStatement.FieldAssignment assignment -> {
          verifyExpression(owner, callable, assignment.receiver());
          verifyExpression(owner, callable, assignment.value());
          requireNonNullableReceiver(owner, assignment.receiver().type(), "field assignment");
          CoreType fieldType =
              instantiatedFieldType(owner, assignment.receiver().type(), assignment.field());
          requireAssignable(owner, fieldType, owner, assignment.value().type(), "field assignment");
        }
        case CoreStatement.IntrinsicAssignment assignment -> {
          verifyExpression(owner, callable, assignment.receiver());
          assignment.index().ifPresent(value -> verifyExpression(owner, callable, value));
          verifyExpression(owner, callable, assignment.value());
          verifyIntrinsicAssignment(owner, assignment);
        }
        case CoreStatement.ReferenceAssignment assignment -> {
          verifyExpression(owner, callable, assignment.reference());
          verifyExpression(owner, callable, assignment.value());
          CoreType target = referenceTarget(owner, assignment.reference().type(), "assignment");
          requireAssignable(
              owner, target, owner, assignment.value().type(), "reference assignment");
        }
        case CoreStatement.ExpressionStatement expression ->
            verifyExpression(owner, callable, expression.expression());
        case CoreStatement.IfStatement conditional -> {
          verifyExpression(owner, callable, conditional.condition());
          requireSameType(
              owner, CoreType.BOOLEAN, owner, conditional.condition().type(), "if condition");
          CoreReferenceFlow.State incoming = referenceFlow.snapshot();
          verifyBlock(owner, callable, conditional.thenBlock());
          CoreReferenceFlow.State thenFlow = referenceFlow.snapshot();
          referenceFlow.replace(incoming);
          verifyBlock(owner, callable, conditional.elseBlock());
          CoreReferenceFlow.State elseFlow = referenceFlow.snapshot();
          referenceFlow.replace(CoreReferenceFlow.merge(incoming, thenFlow, elseFlow));
        }
        case CoreStatement.ConditionalForStatement loop -> {
          verifyExpression(owner, callable, loop.condition());
          requireSameType(
              owner, CoreType.BOOLEAN, owner, loop.condition().type(), "loop condition");
          controls.addFirst(Control.loop());
          CoreReferenceFlow.State incoming = referenceFlow.snapshot();
          verifyBlock(owner, callable, loop.body());
          controls.removeFirst();
          referenceFlow.replace(
              CoreReferenceFlow.merge(incoming, incoming, referenceFlow.snapshot()));
        }
        case CoreStatement.ForStatement loop -> {
          if (local(callable, loop.iteratorLocal()).kind() != CoreLocal.Kind.ITERATOR
              || local(callable, loop.variableLocal()).kind() != CoreLocal.Kind.VARIABLE) {
            throw new IllegalArgumentException("for loop local ABI is invalid");
          }
          loop.indexLocal()
              .ifPresent(
                  index -> {
                    if (index == loop.iteratorLocal() || index == loop.variableLocal()) {
                      throw new IllegalArgumentException("for loop index local ABI is invalid");
                    }
                    CoreLocal local = local(callable, index);
                    if (local.kind() != CoreLocal.Kind.VARIABLE
                        || !absolute(owner, local.type()).equals(CoreType.INTEGER)) {
                      throw new IllegalArgumentException("for loop index local ABI is invalid");
                    }
                  });
          verifyExpression(owner, callable, loop.iterable());
          verifyIteration(owner, callable, loop);
          controls.addFirst(Control.loop());
          CoreReferenceFlow.State incoming = referenceFlow.snapshot();
          List<Integer> loopLocals = new ArrayList<>();
          loopLocals.add(loop.variableLocal());
          loop.indexLocal().ifPresent(loopLocals::add);
          verifyBlock(owner, callable, loop.body(), loopLocals, false);
          controls.removeFirst();
          referenceFlow.replace(
              CoreReferenceFlow.merge(incoming, incoming, referenceFlow.snapshot()));
        }
        case CoreStatement.TryStatement tried -> {
          if (tried.catches().isEmpty() && tried.finallyBlock().isEmpty()) {
            throw new IllegalArgumentException("core try requires catch or finally");
          }
          CoreReferenceFlow.State incoming = referenceFlow.snapshot();
          List<CoreReferenceFlow.State> completing = new ArrayList<>();
          verifyBlock(owner, callable, tried.body());
          if (!definitelyExits(tried.body())) completing.add(referenceFlow.snapshot());
          List<CoreType> preceding = new ArrayList<>();
          for (CoreCatchClause clause : tried.catches()) {
            requireExceptionType(owner, clause.type(), "catch");
            CoreLocal target = local(callable, clause.localIndex());
            if (target.kind() != CoreLocal.Kind.VARIABLE) {
              throw new IllegalArgumentException("catch binding must be a variable local");
            }
            requireSameType(owner, target.type(), owner, clause.type(), "catch binding");
            CoreType absoluteType = absolute(owner, clause.type());
            if (preceding.stream().anyMatch(previous -> isAssignable(previous, absoluteType))) {
              throw new IllegalArgumentException("core catch type is already covered");
            }
            preceding.add(absoluteType);
            referenceFlow.replace(incoming);
            verifyBlock(owner, callable, clause.body(), List.of(clause.localIndex()), false);
            if (!definitelyExits(clause.body())) completing.add(referenceFlow.snapshot());
          }
          CoreReferenceFlow.State normal = mergeCompletingReferenceFlows(incoming, completing);
          referenceFlow.replace(normal);
          if (tried.finallyBlock().isPresent()) {
            referenceFlow.replace(incoming);
            CoreReferenceFlow.Writes writes = referenceFlow.trackWrites();
            try (writes) {
              verifyBlock(owner, callable, tried.finallyBlock().orElseThrow());
            }
            CoreReferenceFlow.State finalFlow = referenceFlow.snapshot();
            referenceFlow.replace(CoreReferenceFlow.overlay(normal, finalFlow, writes.locals()));
          }
        }
        case CoreStatement.ThrowStatement thrown -> {
          verifyExpression(owner, callable, thrown.exception());
          requireExceptionType(owner, thrown.exception().type(), "throw");
        }
        case CoreStatement.ReturnStatement returned -> {
          if (returned.value().isEmpty()) {
            requireSameType(owner, CoreType.VOID, owner, callable.returnType(), "return");
          } else {
            CoreExpression value = returned.value().orElseThrow();
            verifyExpression(owner, callable, value);
            requireAssignable(owner, callable.returnType(), owner, value.type(), "return");
          }
        }
        case CoreStatement.YieldStatement yielded -> {
          if (controls.isEmpty() || controls.getFirst().kind() != ControlKind.SWITCH) {
            throw new IllegalArgumentException("yield is only valid inside switch");
          }
          verifyExpression(owner, callable, yielded.value());
          requireAssignable(
              owner,
              controls.getFirst().yieldType(),
              owner,
              yielded.value().type(),
              "switch yield");
          if (yielded.value().type() instanceof CoreType.Reference) {
            controls.getFirst().mergeReferenceLifetime(referenceLifetime(yielded.value()));
          }
        }
        case CoreStatement.BreakStatement ignored -> {
          if (controls.isEmpty() || controls.getFirst().kind() != ControlKind.LOOP) {
            throw new IllegalArgumentException("break is only valid inside loop");
          }
        }
        case CoreStatement.ContinueStatement ignored -> {
          if (controls.stream().noneMatch(value -> value.kind() == ControlKind.LOOP)) {
            throw new IllegalArgumentException("continue is only valid inside loop");
          }
        }
      }
    }
    referenceFlow.pop();
  }

  private void updateReferenceLifetime(int destination, CoreExpression value) {
    LexicalLifetime source = referenceLifetime(value);
    if (!source.outlives(referenceFlow.storageLifetime(destination))) {
      throw new IllegalArgumentException(
          "core reference cannot outlive the addressed storage location");
    }
    referenceFlow.update(destination, source);
  }

  private static CoreReferenceFlow.State mergeCompletingReferenceFlows(
      CoreReferenceFlow.State incoming, List<CoreReferenceFlow.State> flows) {
    if (flows.isEmpty()) return incoming;
    CoreReferenceFlow.State result = flows.getFirst();
    for (int index = 1; index < flows.size(); index++) {
      result = CoreReferenceFlow.merge(incoming, result, flows.get(index));
    }
    return result;
  }

  private void requireExceptionType(DefinitionId owner, CoreType type, String subject) {
    CoreType value = absolute(owner, type);
    if (value.isNullable()
        || !(value instanceof CoreType.Declared declared)
        || declared.category() != CoreValueCategory.IDENTITY
        || !declared.arguments().isEmpty()
        || !(declared.constructor() instanceof CoreTypeConstructor.User)
        || aggregateView(value, exceptionDefinition()) == null) {
      throw new IllegalArgumentException(subject + " requires an Exception type");
    }
  }

  private DefinitionId exceptionDefinition() {
    if (!exceptionDefinitionResolved) indexExceptionDefinition();
    if (resolvedExceptionDefinition == null) {
      throw new IllegalArgumentException("Exception root is absent");
    }
    return resolvedExceptionDefinition;
  }

  private void indexExceptionDefinition() {
    DefinitionId result = resolvedExceptionDefinition;
    for (CoreDefinitionRecord record : program.definitions()) {
      if (!(record.definition() instanceof CoreDefinition.Aggregate aggregate)) continue;
      CoreNominalTypeKey nominal = aggregate.nominalType();
      if (!isExceptionRoot(nominal)) continue;
      verifyExceptionRoot(aggregate);
      if (result != null && !result.equals(record.id())) {
        throw new IllegalArgumentException("Exception root must be unique");
      }
      result = record.id();
    }
    resolvedExceptionDefinition = result;
    exceptionDefinitionResolved = true;
  }

  private static boolean isExceptionRoot(CoreNominalTypeKey nominal) {
    return nominal.packageName().equals(ExceptionAbi.PACKAGE_NAME)
        && nominal.name().equals(ExceptionAbi.TYPE_NAME);
  }

  private static void verifyExceptionRoot(CoreDefinition.Aggregate declaration) {
    if (declaration.nominalType().visibility() != CoreVisibility.PUBLIC
        || declaration.valueCategory() != CoreValueCategory.IDENTITY
        || !declaration.typeParameters().isEmpty()
        || declaration.parentType().isPresent()
        || declaration.fieldCount() != 1
        || declaration.fields().size() != 1
        || declaration.fields().getFirst().ordinal() != ExceptionAbi.MESSAGE_FIELD_ORDINAL
        || !declaration.fields().getFirst().type().equals(CoreType.STRING)) {
      throw new IllegalArgumentException("Exception root ABI is invalid");
    }
  }

  private LexicalLifetime referenceLifetime(CoreExpression expression) {
    return switch (expression) {
      case CoreExpression.AddressLocal address ->
          referenceFlow.storageLifetime(address.localIndex());
      case CoreExpression.AddressField ignored -> LexicalLifetime.longLived();
      case CoreExpression.LocalRead read -> referenceFlow.referenceLifetime(read.localIndex());
      case CoreExpression.Switch switched -> {
        LexicalLifetime lifetime = referenceFlow.expressionLifetime(switched);
        yield lifetime == null ? LexicalLifetime.unusable() : lifetime;
      }
      default -> LexicalLifetime.unusable();
    };
  }

  private void verifyExpression(
      DefinitionId owner, CoreDefinition.Callable callable, CoreExpression expression) {
    if (expression instanceof CoreExpression.Call
        || expression instanceof CoreExpression.InterfaceCall
        || expression instanceof CoreExpression.Intrinsic
        || expression.type().equals(CoreType.VOID)) {
      verifyReturnType(owner, expression.type(), callable.reifiedTypeLocals().size());
    } else {
      verifyValueType(owner, expression.type(), callable.reifiedTypeLocals().size());
    }
    switch (expression) {
      case CoreExpression.Literal literal -> verifyLiteral(owner, literal);
      case CoreExpression.NullLiteral literal -> {
        if (!literal.type().isNullable()) {
          throw new IllegalArgumentException("null literal requires a nullable core type");
        }
      }
      case CoreExpression.CollectionLiteral collection -> {
        verifyRuntimeType(owner, callable, collection.runtimeType());
        requireSameType(
            owner,
            collection.runtimeType().template(),
            owner,
            collection.type(),
            "collection runtime type");
        if (!matchesCollectionLiteral(owner, collection)) {
          throw new IllegalArgumentException("collection literal does not match its builtin ABI");
        }
        collection.elements().forEach(value -> verifyExpression(owner, callable, value));
        CoreType absoluteType = absolute(owner, collection.type());
        if (!(absoluteType instanceof CoreType.Declared declared)
            || declared.arguments().size() != 1) {
          throw new IllegalArgumentException("collection literal requires one element type");
        }
        CoreType elementType = declared.arguments().getFirst();
        collection
            .elements()
            .forEach(
                value ->
                    requireAssignable(
                        owner, elementType, owner, value.type(), "collection element"));
      }
      case CoreExpression.LocalRead read -> {
        CoreLocal local = local(callable, read.localIndex());
        referenceFlow.requireDeclared(read.localIndex());
        requireAssignable(owner, local.type(), owner, read.type(), "local read");
        if (local.type() instanceof CoreType.Reference) {
          referenceFlow.referenceLifetime(read.localIndex());
        }
      }
      case CoreExpression.FieldRead read -> {
        verifyExpression(owner, callable, read.receiver());
        verifyReceiverSafety(owner, read.receiver().type(), read.nullSafe(), "field read");
        CoreType fieldType = instantiatedFieldType(owner, read.receiver().type(), read.field());
        requireSameType(
            owner,
            safeResult(fieldType, read.nullSafe(), read.receiver().type()),
            owner,
            read.type(),
            "field read");
      }
      case CoreExpression.AddressLocal address -> {
        CoreLocal local = local(callable, address.localIndex());
        if (local.kind() != CoreLocal.Kind.VARIABLE && local.kind() != CoreLocal.Kind.PARAMETER) {
          throw new IllegalArgumentException("address target is not mutable local storage");
        }
        CoreType target = referenceTarget(owner, address.type(), "local address");
        requireSameType(owner, local.type(), owner, target, "local address");
        referenceFlow.storageLifetime(address.localIndex());
      }
      case CoreExpression.AddressField address -> {
        verifyExpression(owner, callable, address.receiver());
        requireNonNullableReceiver(owner, address.receiver().type(), "field address");
        CoreType receiver = nonNullable(absolute(owner, address.receiver().type()));
        if (!(receiver instanceof CoreType.Declared declared)
            || declared.category() != CoreValueCategory.IDENTITY) {
          throw new IllegalArgumentException("field address requires an identity receiver");
        }
        CoreType fieldType =
            instantiatedFieldType(owner, address.receiver().type(), address.field());
        CoreType target = referenceTarget(owner, address.type(), "field address");
        requireSameType(owner, fieldType, owner, target, "field address");
      }
      case CoreExpression.Dereference dereference -> {
        verifyExpression(owner, callable, dereference.reference());
        CoreType target = referenceTarget(owner, dereference.reference().type(), "dereference");
        requireSameType(owner, target, owner, dereference.type(), "dereference");
      }
      case CoreExpression.EnumConstruct construct ->
          verifyEnumConstruct(owner, callable, construct);
      case CoreExpression.Unary unary -> {
        verifyExpression(owner, callable, unary.operand());
        verifyUnary(owner, unary);
      }
      case CoreExpression.Binary binary -> {
        verifyExpression(owner, callable, binary.left());
        verifyExpression(owner, callable, binary.right());
        verifyBinary(owner, binary);
      }
      case CoreExpression.Switch switched -> verifySwitch(owner, callable, switched);
      case CoreExpression.Index index -> {
        verifyExpression(owner, callable, index.receiver());
        verifyExpression(owner, callable, index.index());
        verifyIndex(owner, index);
      }
      case CoreExpression.CopyObject copied -> {
        verifyExpression(owner, callable, copied.receiver());
        verifyReceiverSafety(owner, copied.receiver().type(), copied.nullSafe(), "copy");
        CoreType receiver = nonNullable(absolute(owner, copied.receiver().type()));
        if (!(receiver instanceof CoreType.Declared declared)
            || declared.category() != CoreValueCategory.IDENTITY) {
          throw new IllegalArgumentException("copy requires an identity receiver");
        }
        requireSameType(
            owner,
            safeResult(copied.receiver().type(), copied.nullSafe(), copied.receiver().type()),
            owner,
            copied.type(),
            "copy result");
      }
      case CoreExpression.Closure closure -> verifyClosure(owner, callable, closure);
      case CoreExpression.Invoke invoke -> verifyInvoke(owner, callable, invoke);
      case CoreExpression.Call call -> verifyCall(owner, callable, call);
      case CoreExpression.InterfaceCall call -> verifyInterfaceCall(owner, callable, call);
      case CoreExpression.Construct construct -> verifyConstruct(owner, callable, construct);
      case CoreExpression.Intrinsic intrinsic -> {
        intrinsic.receiver().ifPresent(value -> verifyExpression(owner, callable, value));
        intrinsic
            .arguments()
            .forEach(argument -> verifyExpression(owner, callable, argument.value()));
        intrinsic.runtimeType().ifPresent(type -> verifyRuntimeType(owner, callable, type));
        verifyIntrinsic(owner, intrinsic);
      }
    }
  }

  private void verifyLiteral(DefinitionId owner, CoreExpression.Literal literal) {
    CoreType expected =
        switch (literal.value()) {
          case Integer ignored ->
              absolute(owner, literal.type()).equals(CoreType.CODE_POINT)
                  ? CoreType.CODE_POINT
                  : CoreType.INTEGER;
          case Long ignored -> CoreType.LONG;
          case Float ignored -> CoreType.FLOAT;
          case Double ignored -> CoreType.DOUBLE;
          case Boolean ignored -> CoreType.BOOLEAN;
          case String ignored -> CoreType.STRING;
          default -> throw new IllegalArgumentException("unsupported core literal value");
        };
    requireSameType(owner, expected, owner, literal.type(), "literal");
  }

  private CoreType referenceTarget(DefinitionId owner, CoreType type, String subject) {
    CoreType absolute = absolute(owner, type);
    if (!(absolute instanceof CoreType.Reference reference)) {
      throw new IllegalArgumentException(subject + " requires a reference type");
    }
    return reference.target();
  }

  private void verifyUnary(DefinitionId owner, CoreExpression.Unary unary) {
    CoreType expected = absolute(owner, unary.operand().type());
    if (unary.operator() == CoreUnaryOperator.NOT) expected = CoreType.BOOLEAN;
    else if (!isNumericLeaf(expected)) {
      throw new IllegalArgumentException("numeric unary operand requires a concrete leaf type");
    }
    requireSameType(owner, expected, owner, unary.operand().type(), "unary operand");
    requireSameType(owner, expected, owner, unary.type(), "unary result");
  }

  private void verifyBinary(DefinitionId owner, CoreExpression.Binary binary) {
    CoreType left = absolute(owner, binary.left().type());
    CoreType right = absolute(owner, binary.right().type());
    CoreType result = absolute(owner, binary.type());
    switch (binary.operator()) {
      case ADD, SUBTRACT, MULTIPLY, DIVIDE, REMAINDER -> {
        if (!isNumericLeaf(left)) {
          throw new IllegalArgumentException("binary left operand requires a numeric leaf type");
        }
        requireSameType(left, right, "binary right operand");
        requireSameType(left, result, "binary result");
      }
      case STRING_CONCAT -> {
        requireSameType(CoreType.STRING, left, "binary left operand");
        requireSameType(CoreType.STRING, right, "binary right operand");
        requireSameType(CoreType.STRING, result, "binary result");
      }
      case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
        if (!isNumericLeaf(left)) {
          throw new IllegalArgumentException(
              "comparison left operand requires a numeric leaf type");
        }
        requireSameType(left, right, "comparison right operand");
        requireSameType(CoreType.BOOLEAN, result, "comparison result");
      }
      case AND, OR -> {
        requireSameType(CoreType.BOOLEAN, left, "logical left operand");
        requireSameType(CoreType.BOOLEAN, right, "logical right operand");
        requireSameType(CoreType.BOOLEAN, result, "logical result");
      }
      case EQUAL, NOT_EQUAL -> {
        if (!isAssignable(left, right) && !isAssignable(right, left)) {
          throw new IllegalArgumentException("equality operands have incompatible types");
        }
        requireSameType(CoreType.BOOLEAN, result, "equality result");
      }
      case COALESCE -> {
        if (!mayContainNull(left)) {
          throw new IllegalArgumentException("coalesce left operand cannot contain null");
        }
        CoreType expected = nonNullable(left);
        requireAssignable(expected, right, "coalesce right operand");
        requireSameType(expected, result, "coalesce result");
      }
    }
  }

  private void verifyIndex(DefinitionId owner, CoreExpression.Index index) {
    requireNonNullableReceiver(owner, index.receiver().type(), "index read");
    boolean valid =
        builtins.indexCandidates(index.readIntrinsic()).stream()
            .anyMatch(candidate -> matchesIndex(owner, index, candidate));
    if (!valid)
      throw new IllegalArgumentException("index expression does not match its builtin ABI");
  }

  private boolean matchesCollectionLiteral(
      DefinitionId owner, CoreExpression.CollectionLiteral collection) {
    CoreType actual = absolute(owner, collection.type());
    for (BuiltinCatalog.IntrinsicCandidate candidate :
        builtins.intrinsicCandidates(collection.materializer())) {
      if (candidate.receiver().isPresent()
          || !candidate.parameters().isEmpty()
          || !candidate.runtimeType()) {
        continue;
      }
      Map<String, CoreType> substitutions = new LinkedHashMap<>();
      if (bindPattern(actual, candidate.result(), substitutions)) return true;
    }
    return false;
  }

  private boolean matchesIndex(
      DefinitionId owner, CoreExpression.Index index, BuiltinCatalog.IndexCandidate candidate) {
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (!bindPattern(
        nonNullable(absolute(owner, index.receiver().type())),
        candidate.receiver(),
        substitutions)) {
      return false;
    }
    if (!candidate.writeIntrinsic().equals(index.writeIntrinsic())) return false;
    CoreType expectedIndex = instantiate(candidate.index(), substitutions);
    CoreType expectedResult = instantiate(candidate.result(), substitutions);
    return isAssignable(expectedIndex, absolute(owner, index.index().type()))
        && expectedResult.equals(absolute(owner, index.type()));
  }

  private void verifyIntrinsic(DefinitionId owner, CoreExpression.Intrinsic intrinsic) {
    boolean valid =
        builtins.intrinsicCandidates(intrinsic.intrinsic()).stream()
            .anyMatch(candidate -> matchesIntrinsic(owner, intrinsic, candidate));
    if (!valid) {
      throw new IllegalArgumentException(
          "intrinsic expression does not match its builtin ABI: " + intrinsic.intrinsic());
    }
    if (intrinsic.intrinsic() == dev.w0fv1.norm.abi.IntrinsicId.CLASS_ANNOTATION
        || intrinsic.intrinsic() == dev.w0fv1.norm.abi.IntrinsicId.FIELD_ANNOTATION) {
      CoreType annotationType = nonNullable(absolute(owner, intrinsic.type()));
      if (!(annotationType instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)
          || !(program.definition(resolve(owner, user.definition())).orElse(null)
              instanceof CoreDefinition.Aggregate annotation)
          || annotation.kind() != CoreAggregateKind.ANNOTATION) {
        throw new IllegalArgumentException("annotation query result must name an annotation");
      }
    }
  }

  private boolean matchesIntrinsic(
      DefinitionId owner,
      CoreExpression.Intrinsic intrinsic,
      BuiltinCatalog.IntrinsicCandidate candidate) {
    if (candidate.receiver().isPresent() != intrinsic.receiver().isPresent()
        || candidate.runtimeType() != intrinsic.runtimeType().isPresent()
        || intrinsic.nullSafe() && intrinsic.receiver().isEmpty()
        || !denseArguments(intrinsic.arguments(), candidate.parameters().size())) {
      return false;
    }
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (candidate.receiver().isPresent()) {
      CoreType actualReceiver = absolute(owner, intrinsic.receiver().orElseThrow().type());
      if (!intrinsic.nullSafe() && actualReceiver.isNullable()) return false;
      if (!bindPattern(
          nonNullable(actualReceiver), candidate.receiver().orElseThrow(), substitutions)) {
        return false;
      }
    }
    if (candidate.runtimeType()) {
      CoreType runtimeTemplate = absolute(owner, intrinsic.runtimeType().orElseThrow().template());
      if (!bindPattern(runtimeTemplate, candidate.result(), substitutions)) return false;
    }
    for (CoreArgument argument : intrinsic.arguments()) {
      SemanticType parameter = candidate.parameters().get(argument.parameterIndex()).type();
      CoreType actual = absolute(owner, argument.value().type());
      Map<String, CoreType> argumentBindings = new LinkedHashMap<>(substitutions);
      if (bindPattern(actual, parameter, argumentBindings)) {
        substitutions.putAll(argumentBindings);
      }
    }
    if (!candidate.runtimeType() && containsUnbound(candidate.result(), substitutions)) {
      CoreType resultTemplate = absolute(owner, intrinsic.type());
      if (intrinsic.nullSafe()) resultTemplate = nonNullable(resultTemplate);
      if (!bindPattern(resultTemplate, candidate.result(), substitutions)) return false;
    }
    for (CoreArgument argument : intrinsic.arguments()) {
      SemanticType parameter = candidate.parameters().get(argument.parameterIndex()).type();
      CoreType actual = absolute(owner, argument.value().type());
      if (containsUnbound(parameter, substitutions)) return false;
      CoreType expected = instantiate(parameter, substitutions);
      if (!expected.equals(CoreType.DYNAMIC) && !isAssignable(expected, actual)) {
        return false;
      }
    }
    if (containsUnbound(candidate.result(), substitutions)) return false;
    CoreType result = instantiate(candidate.result(), substitutions);
    CoreType receiver =
        intrinsic
            .receiver()
            .map(CoreExpression::type)
            .map(type -> absolute(owner, type))
            .orElse(CoreType.DYNAMIC);
    result = safeResult(result, intrinsic.nullSafe(), receiver);
    return result.equals(absolute(owner, intrinsic.type()));
  }

  private void verifyIntrinsicAssignment(
      DefinitionId owner, CoreStatement.IntrinsicAssignment assignment) {
    requireNonNullableReceiver(owner, assignment.receiver().type(), "intrinsic assignment");
    boolean valid =
        builtins.writeCandidates(assignment.intrinsic()).stream()
            .anyMatch(candidate -> matchesWrite(owner, assignment, candidate));
    if (!valid) {
      throw new IllegalArgumentException("intrinsic assignment does not match its builtin ABI");
    }
  }

  private boolean matchesWrite(
      DefinitionId owner,
      CoreStatement.IntrinsicAssignment assignment,
      BuiltinCatalog.WriteCandidate candidate) {
    if (candidate.index().isPresent() != assignment.index().isPresent()) return false;
    Map<String, CoreType> substitutions = new LinkedHashMap<>();
    if (!bindPattern(
        nonNullable(absolute(owner, assignment.receiver().type())),
        candidate.receiver(),
        substitutions)) {
      return false;
    }
    if (candidate.index().isPresent()) {
      CoreType expectedIndex = instantiate(candidate.index().orElseThrow(), substitutions);
      if (!isAssignable(expectedIndex, absolute(owner, assignment.index().orElseThrow().type()))) {
        return false;
      }
    }
    CoreType expectedValue = instantiate(candidate.value(), substitutions);
    return isAssignable(expectedValue, absolute(owner, assignment.value().type()));
  }

  private void verifyIteration(
      DefinitionId owner, CoreDefinition.Callable callable, CoreStatement.ForStatement loop) {
    requireNonNullableReceiver(owner, loop.iterable().type(), "iteration");
    CoreType variable = absolute(owner, local(callable, loop.variableLocal()).type());
    switch (loop.iteration()) {
      case CoreIteration.Builtin builtin -> {
        boolean valid =
            builtins.iterationCandidates(builtin.intrinsic()).stream()
                .anyMatch(
                    candidate -> {
                      Map<String, CoreType> substitutions = new LinkedHashMap<>();
                      return bindPattern(
                              nonNullable(absolute(owner, loop.iterable().type())),
                              candidate.receiver(),
                              substitutions)
                          && instantiate(candidate.element(), substitutions).equals(variable);
                    });
        if (!valid) {
          throw new IllegalArgumentException("iteration does not match its builtin ABI");
        }
      }
      case CoreIteration.Interface protocol -> {
        CoreType iterator =
            verifyIterationRequirement(
                owner, callable, loop.iterable().type(), protocol.iteratorRequirement());
        CoreType hasNext =
            verifyIterationRequirement(owner, callable, iterator, protocol.hasNextRequirement());
        CoreType next =
            verifyIterationRequirement(owner, callable, iterator, protocol.nextRequirement());
        requireSameType(CoreType.BOOLEAN, hasNext, "iteration hasNext result");
        requireSameType(variable, next, "iteration next result");
      }
    }
  }

  private CoreType verifyIterationRequirement(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CoreType receiverType,
      CoreDefinitionLink requirementLink) {
    DefinitionId requirementId = resolve(owner, requirementLink);
    CoreDefinition target = program.definition(requirementId).orElseThrow();
    if (!(target instanceof CoreDefinition.InterfaceMethod requirement)
        || !requirement.typeParameters().isEmpty()
        || !requirement.parameterTypes().isEmpty()) {
      throw new IllegalArgumentException("iteration requirement has the wrong callable ABI");
    }
    InterfaceInstance required =
        interfaceInstance(requirementId, requirement.receiverInterfaceType());
    CoreType actualReceiver = nonNullable(absolute(owner, receiverType));
    CoreType realized = realizedInterface(actualReceiver, required.definition());
    if (realized == null && actualReceiver instanceof CoreType.Parameter parameter) {
      CoreTypeParameter declaration =
          callable.typeParameters().stream()
              .filter(candidate -> candidate.index() == parameter.index())
              .findFirst()
              .orElse(null);
      if (declaration != null && declaration.upperBound().isPresent()) {
        realized =
            realizedInterface(
                nonNullable(absolute(owner, declaration.upperBound().orElseThrow())),
                required.definition());
      }
    }
    if (realized == null) {
      throw new IllegalArgumentException("iteration requirement has the wrong receiver ABI");
    }
    InterfaceInstance receiver = interfaceInstance(owner, realized);
    List<CoreType> substitutions = interfaceSubstitutions(receiver, required.definition());
    if (substitutions == null) {
      throw new IllegalArgumentException("iteration requirement has the wrong receiver ABI");
    }
    return absolute(requirementId, requirement.returnType()).substitute(substitutions::get);
  }

  private boolean bindPattern(
      CoreType actual, SemanticType pattern, Map<String, CoreType> substitutions) {
    if (pattern.kind() == SemanticType.Kind.ERROR) return true;
    if (pattern.kind() == SemanticType.Kind.EXISTENTIAL) return true;
    if (pattern.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      CoreType previous = substitutions.get(pattern.identity());
      if (previous != null) {
        CoreType expected = pattern.isNullable() ? previous.asNullable() : previous;
        return expected.equals(actual);
      }
      CoreType captured = pattern.isNullable() ? nonNullable(actual) : actual;
      substitutions.put(pattern.identity(), captured);
      return true;
    }
    if (pattern.kind() == SemanticType.Kind.VOID) return actual.equals(CoreType.VOID);
    if (pattern.kind() == SemanticType.Kind.NULL) return actual.equals(CoreType.NULL);
    if (pattern.isFunction()) {
      if (pattern.isUnknownFunction()) {
        return actual instanceof CoreType.Function function
            && function.nullability()
                == (pattern.isNullable() ? CoreNullability.NULLABLE : CoreNullability.NON_NULL);
      }
      if (!(actual instanceof CoreType.Function function)
          || function.isNullable() != pattern.isNullable()
          || function.parameterTypes().size() != pattern.functionParameterTypes().size()
          || !bindPattern(function.returnType(), pattern.functionReturnType(), substitutions)) {
        return false;
      }
      for (int index = 0; index < function.parameterTypes().size(); index++) {
        if (!bindPattern(
            function.parameterTypes().get(index),
            pattern.functionParameterTypes().get(index),
            substitutions)) {
          return false;
        }
      }
      return true;
    }
    if (!(actual instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)
        || !builtin.id().value().equals(pattern.identity())
        || declared.arguments().size() != pattern.arguments().size()
        || declared.category() != category(pattern.category())
        || declared.isNullable() != pattern.isNullable()) {
      return false;
    }
    for (int index = 0; index < pattern.arguments().size(); index++) {
      if (!bindPattern(
          declared.arguments().get(index), pattern.arguments().get(index), substitutions)) {
        return false;
      }
    }
    return true;
  }

  private static boolean containsUnbound(
      SemanticType pattern, Map<String, CoreType> substitutions) {
    if (pattern.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      return !substitutions.containsKey(pattern.identity());
    }
    if (pattern.isFunction()) {
      if (pattern.isUnknownFunction()) return false;
      return containsUnbound(pattern.functionReturnType(), substitutions)
          || pattern.functionParameterTypes().stream()
              .anyMatch(parameter -> containsUnbound(parameter, substitutions));
    }
    return pattern.arguments().stream()
        .anyMatch(argument -> containsUnbound(argument, substitutions));
  }

  private CoreType instantiate(SemanticType pattern, Map<String, CoreType> substitutions) {
    return switch (pattern.kind()) {
      case TYPE_PARAMETER -> {
        CoreType type = substitutions.get(pattern.identity());
        if (type == null)
          throw new IllegalArgumentException("builtin type parameter is unresolved");
        yield pattern.isNullable() ? type.asNullable() : type;
      }
      case DECLARED ->
          pattern.isFunction()
              ? new CoreType.Function(
                  pattern.isUnknownFunction()
                      ? CoreType.EXISTENTIAL
                      : instantiate(pattern.functionReturnType(), substitutions),
                  pattern.isUnknownFunction()
                      ? List.of()
                      : pattern.functionParameterTypes().stream()
                          .map(argument -> instantiate(argument, substitutions))
                          .toList(),
                  pattern.isNullable() ? CoreNullability.NULLABLE : CoreNullability.NON_NULL)
              : new CoreType.Declared(
                  new CoreTypeConstructor.Builtin(new BuiltinTypeId(pattern.identity())),
                  pattern.arguments().stream()
                      .map(argument -> instantiate(argument, substitutions))
                      .toList(),
                  category(pattern.category()),
                  pattern.isNullable() ? CoreNullability.NULLABLE : CoreNullability.NON_NULL);
      case REFERENCE ->
          new CoreType.Reference(instantiate(pattern.referenceTarget(), substitutions));
      case VOID -> CoreType.VOID;
      case NULL -> CoreType.NULL;
      case ERROR -> CoreType.DYNAMIC;
      case EXISTENTIAL -> CoreType.EXISTENTIAL;
    };
  }

  private static CoreValueCategory category(ValueCategory category) {
    return switch (category) {
      case VALUE -> CoreValueCategory.VALUE;
      case IDENTITY -> CoreValueCategory.IDENTITY;
      case POLYMORPHIC -> CoreValueCategory.POLYMORPHIC;
      case DYNAMIC -> CoreValueCategory.DYNAMIC;
      case VOID -> CoreValueCategory.VOID;
    };
  }

  private void verifyReceiverSafety(
      DefinitionId owner, CoreType receiver, boolean nullSafe, String subject) {
    CoreType actual = absolute(owner, receiver);
    if (!nullSafe && actual.isNullable()) {
      throw new IllegalArgumentException(subject + " requires null-safe access");
    }
  }

  private void requireNonNullableReceiver(DefinitionId owner, CoreType receiver, String subject) {
    if (absolute(owner, receiver).isNullable()) {
      throw new IllegalArgumentException(subject + " requires a non-null receiver");
    }
  }

  private void verifyCall(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Call call) {
    DefinitionId targetId = resolve(owner, call.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Callable target)) {
      throw new IllegalArgumentException("call target is not callable");
    }
    call.receiver().ifPresent(value -> verifyExpression(owner, caller, value));
    call.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    call.reifiedArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    call.receiverTypeArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    if (target.hasReceiver() != call.receiver().isPresent()) {
      throw new IllegalArgumentException("call receiver does not match the target ABI");
    }
    if (call.nullSafe() && call.receiver().isEmpty()) {
      throw new IllegalArgumentException("null-safe call requires a receiver");
    }
    List<CoreType> substitutions = new ArrayList<>();
    if (target.hasReceiver()) {
      CoreType actualReceiver = absolute(owner, call.receiver().orElseThrow().type());
      CoreType nonNullableReceiver = nonNullable(actualReceiver);
      CoreType dispatchReceiver = effectiveClassReceiver(owner, nonNullableReceiver);
      if (!(dispatchReceiver instanceof CoreType.Declared declaredReceiver)) {
        throw new IllegalArgumentException("method receiver is not a declared type");
      }
      verifyMethodDispatch(dispatchReceiver, targetId, call.virtual(), "call");
      if (call.receiverTypeArguments().isEmpty()) {
        substitutions.addAll(declaredReceiver.arguments());
      } else {
        call.receiverTypeArguments().stream()
            .map(CoreRuntimeType::template)
            .map(type -> absolute(owner, type))
            .forEach(substitutions::add);
      }
      CoreType expectedReceiver =
          absolute(targetId, target.receiverType().orElseThrow()).substitute(substitutions::get);
      if (!isAssignable(expectedReceiver, nonNullableReceiver)
          && !isAssignable(expectedReceiver, dispatchReceiver)) {
        throw new IllegalArgumentException("method receiver type does not match the target ABI");
      }
      if (!call.nullSafe() && actualReceiver.isNullable()) {
        throw new IllegalArgumentException("nullable receiver requires a null-safe call");
      }
    }
    call.reifiedArguments().stream()
        .map(CoreRuntimeType::template)
        .map(type -> absolute(owner, type))
        .forEach(substitutions::add);
    if (substitutions.size() != target.reifiedTypeLocals().size()) {
      throw new IllegalArgumentException("call reified arguments do not match the target ABI");
    }
    verifyTypeArgumentBounds(targetId, target.typeParameters(), substitutions, owner);
    verifyDenseArguments(call.arguments(), target.parameterTypes().size());
    for (CoreArgument argument : call.arguments()) {
      CoreType expected =
          absolute(targetId, target.parameterTypes().get(argument.parameterIndex()))
              .substitute(substitutions::get);
      requireAssignable(expected, absolute(owner, argument.value().type()), "call argument");
    }
    CoreType result = absolute(targetId, target.returnType()).substitute(substitutions::get);
    CoreType receiverType = call.receiver().map(CoreExpression::type).orElse(CoreType.DYNAMIC);
    result = safeResult(result, call.nullSafe(), receiverType);
    requireSameType(result, absolute(owner, call.type()), "call result");
  }

  private void verifyClosure(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Closure closure) {
    DefinitionId targetId = resolve(owner, closure.target());
    CoreDefinition definition = program.definition(targetId).orElseThrow();
    if (!(definition instanceof CoreDefinition.Callable target)) {
      throw new IllegalArgumentException("closure target is not callable");
    }
    closure.receiver().ifPresent(value -> verifyExpression(owner, caller, value));
    closure.captures().forEach(value -> verifyExpression(owner, caller, value));
    closure.reifiedArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    closure.receiverTypeArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    if (!target.hasReceiver() && closure.receiver().isPresent()) {
      throw new IllegalArgumentException("closure receiver does not match the target ABI");
    }
    if (target.captureTypes().size() != closure.captures().size()) {
      throw new IllegalArgumentException("closure captures do not match the target ABI");
    }
    List<CoreType> substitutions = new ArrayList<>();
    CoreType unboundReceiver = null;
    if (target.hasReceiver()) {
      if (closure.receiver().isPresent()) {
        CoreType receiver = nonNullable(absolute(owner, closure.receiver().orElseThrow().type()));
        CoreType dispatchReceiver = effectiveClassReceiver(owner, receiver);
        if (!(dispatchReceiver instanceof CoreType.Declared declared)) {
          throw new IllegalArgumentException("bound method receiver is not declared");
        }
        verifyMethodDispatch(dispatchReceiver, targetId, closure.virtual(), "closure");
        if (closure.receiverTypeArguments().isEmpty()) {
          substitutions.addAll(declared.arguments());
        } else {
          closure.receiverTypeArguments().stream()
              .map(CoreRuntimeType::template)
              .map(type -> absolute(owner, type))
              .forEach(substitutions::add);
        }
        CoreType expectedReceiver =
            absolute(targetId, target.receiverType().orElseThrow()).substitute(substitutions::get);
        if (!isAssignable(expectedReceiver, receiver)
            && !isAssignable(expectedReceiver, dispatchReceiver)) {
          throw new IllegalArgumentException("bound method receiver does not match the target ABI");
        }
      } else {
        closure.receiverTypeArguments().stream()
            .map(CoreRuntimeType::template)
            .map(type -> absolute(owner, type))
            .forEach(substitutions::add);
        unboundReceiver = absolute(targetId, target.receiverType().orElseThrow());
        if (!substitutions.isEmpty()) {
          unboundReceiver = unboundReceiver.substitute(substitutions::get);
        }
        verifyMethodDispatch(unboundReceiver, targetId, closure.virtual(), "closure");
      }
    }
    closure.reifiedArguments().stream()
        .map(CoreRuntimeType::template)
        .map(type -> absolute(owner, type))
        .forEach(substitutions::add);
    if (substitutions.size() != target.reifiedTypeLocals().size()) {
      throw new IllegalArgumentException("closure reified arguments do not match the target ABI");
    }
    verifyTypeArgumentBounds(targetId, target.typeParameters(), substitutions, owner);
    for (int index = 0; index < closure.captures().size(); index++) {
      CoreType expected = absolute(targetId, target.captureTypes().get(index));
      if (!substitutions.isEmpty()) expected = expected.substitute(substitutions::get);
      requireAssignable(
          expected, absolute(owner, closure.captures().get(index).type()), "closure capture");
    }
    List<CoreType> parameters = new ArrayList<>();
    if (unboundReceiver != null) parameters.add(unboundReceiver);
    target.parameterTypes().stream()
        .map(value -> absolute(targetId, value))
        .map(value -> substitutions.isEmpty() ? value : value.substitute(substitutions::get))
        .forEach(parameters::add);
    CoreType result = absolute(targetId, target.returnType());
    if (!substitutions.isEmpty()) result = result.substitute(substitutions::get);
    CoreType expected = new CoreType.Function(result, parameters, CoreNullability.NON_NULL);
    requireSameType(expected, absolute(owner, closure.type()), "closure type");
  }

  private void verifyMethodDispatch(
      CoreType receiver, DefinitionId slot, boolean virtual, String subject) {
    boolean dispatched = false;
    if (receiver instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user) {
      DefinitionId aggregateId = resolveExternal(user.definition());
      CoreDefinition definition = program.definition(aggregateId).orElseThrow();
      if (definition instanceof CoreDefinition.Aggregate aggregate) {
        dispatched =
            aggregate.dispatch().stream()
                .anyMatch(dispatch -> resolve(aggregateId, dispatch.slot()).equals(slot));
      }
    }
    if (dispatched != virtual) {
      throw new IllegalArgumentException(subject + " dispatch mode does not match its method slot");
    }
  }

  private CoreType effectiveClassReceiver(DefinitionId owner, CoreType receiver) {
    if (!(receiver instanceof CoreType.Parameter parameter)) return receiver;
    ParameterContext context = typeParameterContext(owner, parameter.index());
    if (context == null || context.parameter().upperBound().isEmpty()) return receiver;
    CoreType bound =
        nonNullable(absolute(context.owner(), context.parameter().upperBound().orElseThrow()));
    if (!(bound instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(program.definition(resolveExternal(user.definition())).orElseThrow()
            instanceof CoreDefinition.Aggregate)) {
      return receiver;
    }
    return bound;
  }

  private void verifyInvoke(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Invoke invoke) {
    verifyExpression(owner, caller, invoke.callee());
    invoke.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    CoreType callee = nonNullable(absolute(owner, invoke.callee().type()));
    if (!(callee instanceof CoreType.Function function)) {
      throw new IllegalArgumentException("invoked expression is not a function");
    }
    verifyDenseArguments(invoke.arguments(), function.parameterTypes().size());
    for (CoreArgument argument : invoke.arguments()) {
      requireAssignable(
          function.parameterTypes().get(argument.parameterIndex()),
          absolute(owner, argument.value().type()),
          "function argument");
    }
    requireSameType(function.returnType(), absolute(owner, invoke.type()), "function result");
  }

  private void verifyInterfaceCall(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.InterfaceCall call) {
    DefinitionId requirementId = resolve(owner, call.requirement());
    CoreDefinition target = program.definition(requirementId).orElseThrow();
    if (!(target instanceof CoreDefinition.InterfaceMethod requirement)) {
      throw new IllegalArgumentException("interface call target is not an interface method");
    }
    verifyExpression(owner, caller, call.receiver());
    call.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    call.reifiedArguments().forEach(type -> verifyRuntimeType(owner, caller, type));
    verifyReceiverSafety(owner, call.receiver().type(), call.nullSafe(), "interface call");
    CoreType actualReceiver = nonNullable(absolute(owner, call.receiver().type()));
    InterfaceInstance required =
        interfaceInstance(requirementId, requirement.receiverInterfaceType());
    CoreType realized = realizedInterface(actualReceiver, required.definition());
    if (realized == null && actualReceiver instanceof CoreType.Parameter parameter) {
      CoreTypeParameter declaration =
          caller.typeParameters().stream()
              .filter(candidate -> candidate.index() == parameter.index())
              .findFirst()
              .orElse(null);
      if (declaration != null && declaration.upperBound().isPresent()) {
        CoreType upperBound = absolute(owner, declaration.upperBound().orElseThrow());
        realized = realizedInterface(nonNullable(upperBound), required.definition());
      }
    }
    if (realized == null) {
      throw new IllegalArgumentException(
          "interface call receiver does not satisfy the requirement");
    }
    InterfaceInstance instance = interfaceInstance(owner, realized);
    List<CoreType> substitutions = interfaceSubstitutions(instance, required.definition());
    if (substitutions == null) {
      throw new IllegalArgumentException(
          "interface call receiver does not satisfy the requirement");
    }
    substitutions = new ArrayList<>(substitutions);
    call.reifiedArguments().stream()
        .map(CoreRuntimeType::template)
        .map(type -> absolute(owner, type))
        .forEach(substitutions::add);
    if (call.reifiedArguments().size() != requirement.typeParameters().size()) {
      throw new IllegalArgumentException(
          "interface call type arguments do not match the requirement ABI");
    }
    verifyTypeArgumentBounds(requirementId, requirement.typeParameters(), substitutions, owner);
    verifyDenseArguments(call.arguments(), requirement.parameterTypes().size());
    for (CoreArgument argument : call.arguments()) {
      CoreType expected =
          absolute(requirementId, requirement.parameterTypes().get(argument.parameterIndex()))
              .substitute(substitutions::get);
      requireAssignable(
          expected, absolute(owner, argument.value().type()), "interface call argument");
    }
    CoreType result =
        absolute(requirementId, requirement.returnType()).substitute(substitutions::get);
    result = safeResult(result, call.nullSafe(), call.receiver().type());
    requireSameType(result, absolute(owner, call.type()), "interface call result");
  }

  private void verifyConstruct(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.Construct construct) {
    DefinitionId targetId = resolve(owner, construct.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Aggregate target)) {
      throw new IllegalArgumentException("construct target is not an aggregate");
    }
    verifyRuntimeType(owner, caller, construct.runtimeType());
    construct.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    CoreType constructedType = absolute(owner, construct.type());
    requireSameType(
        constructedType,
        absolute(owner, construct.runtimeType().template()),
        "construct runtime type");
    if (!(nonNullable(constructedType) instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(targetId)
        || declared.arguments().size() != target.typeParameters().size()) {
      throw new IllegalArgumentException("constructed type does not match the aggregate ABI");
    }
    DefinitionId initializerId = resolve(owner, construct.initializer());
    if (target.constructors().stream()
        .map(constructor -> resolve(targetId, constructor))
        .noneMatch(initializerId::equals)) {
      throw new IllegalArgumentException("construct initializer does not match the aggregate");
    }
    CoreDefinition definition = program.definition(initializerId).orElseThrow();
    if (!(definition instanceof CoreDefinition.Callable initializer)
        || initializer.receiverType().isEmpty()) {
      throw new IllegalArgumentException("construct initializer is not a constructor");
    }
    verifyDenseArguments(construct.arguments(), initializer.parameterTypes().size());
    List<CoreType> substitutions = new ArrayList<>(declared.arguments());
    for (CoreArgument argument : construct.arguments()) {
      CoreType expected =
          absolute(initializerId, initializer.parameterTypes().get(argument.parameterIndex()))
              .substitute(substitutions::get);
      requireAssignable(expected, absolute(owner, argument.value().type()), "constructor argument");
    }
  }

  private void verifyEnumConstruct(
      DefinitionId owner, CoreDefinition.Callable caller, CoreExpression.EnumConstruct construct) {
    DefinitionId targetId = resolve(owner, construct.target());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    if (!(targetDefinition instanceof CoreDefinition.Enum target)) {
      throw new IllegalArgumentException("enum construct target is not an enum");
    }
    CoreEnumVariant variant =
        target.variants().stream()
            .filter(candidate -> candidate.key().equals(construct.variantKey()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("enum construct variant is absent"));
    verifyRuntimeType(owner, caller, construct.runtimeType());
    construct.arguments().forEach(argument -> verifyExpression(owner, caller, argument.value()));
    CoreType type = absolute(owner, construct.type());
    requireSameType(
        type, absolute(owner, construct.runtimeType().template()), "enum construct runtime type");
    if (type.isNullable()
        || !(type instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !resolveExternal(user.definition()).equals(targetId)
        || declared.arguments().size() != target.typeParameters().size()) {
      throw new IllegalArgumentException("enum construct type does not match its target");
    }
    verifyDenseArguments(construct.arguments(), variant.fields().size());
    for (CoreArgument argument : construct.arguments()) {
      CoreType expected =
          absolute(targetId, variant.fields().get(argument.parameterIndex()).type())
              .substitute(declared.arguments()::get);
      requireAssignable(
          expected, absolute(owner, argument.value().type()), "enum construct argument");
    }
  }

  private void verifySwitch(
      DefinitionId owner, CoreDefinition.Callable callable, CoreExpression.Switch switched) {
    verifyExpression(owner, callable, switched.value());
    CoreType valueType = absolute(owner, switched.value().type());
    PatternCoverage<CoreType> coverage = new PatternCoverage<>(new CorePatternDomain(owner));
    List<PatternCoverage.Pattern> previous = new ArrayList<>();
    Control switchControl = Control.switched(switched.type());
    CoreReferenceFlow.State incoming = referenceFlow.snapshot();
    List<CoreReferenceFlow.State> caseFlows = new ArrayList<>();
    for (CoreSwitchCase switchCase : switched.cases()) {
      referenceFlow.replace(incoming);
      PatternCoverage.Pattern pattern =
          verifyPattern(owner, callable, switchCase.pattern(), valueType);
      if (!coverage.isUseful(previous, pattern, valueType)) {
        throw new IllegalArgumentException("core switch case is unreachable");
      }
      previous.add(pattern);
      controls.addFirst(switchControl);
      verifyBlock(owner, callable, switchCase.body(), patternLocals(switchCase.pattern()), false);
      controls.removeFirst();
      caseFlows.add(referenceFlow.snapshot());
      if (!switched.type().equals(CoreType.VOID) && !definitelyYields(switchCase.body())) {
        throw new IllegalArgumentException("core switch expression case does not yield");
      }
    }
    if (!coverage.isExhaustive(previous, valueType)) {
      throw new IllegalArgumentException("core switch is not exhaustive");
    }
    if (!caseFlows.isEmpty()) {
      CoreReferenceFlow.State merged = caseFlows.getFirst();
      for (int index = 1; index < caseFlows.size(); index++) {
        merged = CoreReferenceFlow.merge(incoming, merged, caseFlows.get(index));
      }
      referenceFlow.replace(merged);
    }
    if (switched.type() instanceof CoreType.Reference) {
      LexicalLifetime lifetime = switchControl.referenceLifetime();
      if (lifetime == null || !lifetime.outlives(referenceFlow.currentLifetime())) {
        throw new IllegalArgumentException(
            "core reference cannot outlive the addressed storage location");
      }
      referenceFlow.recordExpressionLifetime(switched, lifetime);
    }
  }

  private static List<Integer> patternLocals(CorePattern pattern) {
    List<Integer> result = new ArrayList<>();
    collectPatternLocals(pattern, result);
    return List.copyOf(result);
  }

  private static void collectPatternLocals(CorePattern pattern, List<Integer> result) {
    switch (pattern) {
      case CorePattern.Binding binding -> result.add(binding.localIndex());
      case CorePattern.Variant variant ->
          variant.arguments().forEach(argument -> collectPatternLocals(argument, result));
      case CorePattern.Wildcard ignored -> {}
      case CorePattern.Literal ignored -> {}
      case CorePattern.Null ignored -> {}
    }
  }

  private PatternCoverage.Pattern verifyPattern(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CorePattern pattern,
      CoreType expected) {
    if (expected.isNullable() && !(pattern instanceof CorePattern.Null)) {
      if (pattern instanceof CorePattern.Wildcard) return PatternCoverage.Pattern.any();
      return PatternCoverage.Pattern.constructor(
          "$value", List.of(verifyNonNullPattern(owner, callable, pattern, nonNullable(expected))));
    }
    if (pattern instanceof CorePattern.Null) {
      if (!expected.isNullable()) {
        throw new IllegalArgumentException("core null pattern requires nullable type");
      }
      return PatternCoverage.Pattern.constructor("$null", List.of());
    }
    return verifyNonNullPattern(owner, callable, pattern, nonNullable(expected));
  }

  private PatternCoverage.Pattern verifyNonNullPattern(
      DefinitionId owner,
      CoreDefinition.Callable callable,
      CorePattern pattern,
      CoreType expected) {
    return switch (pattern) {
      case CorePattern.Wildcard ignored -> PatternCoverage.Pattern.any();
      case CorePattern.Binding binding -> {
        CoreLocal local = local(callable, binding.localIndex());
        if (local.kind() != CoreLocal.Kind.VARIABLE) {
          throw new IllegalArgumentException("core pattern binding requires variable local");
        }
        verifyValueType(owner, binding.type(), callable.reifiedTypeLocals().size());
        requireSameType(owner, binding.type(), owner, local.type(), "pattern binding local");
        requireSameType(expected, absolute(owner, binding.type()), "pattern binding type");
        yield PatternCoverage.Pattern.any();
      }
      case CorePattern.Variant variant -> {
        EnumInstance instance = enumInstance(expected);
        CoreEnumVariant declaration =
            instance.declaration().variants().stream()
                .filter(candidate -> candidate.key().equals(variant.variantKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("core pattern variant is absent"));
        if (variant.arguments().size() != declaration.fields().size()) {
          throw new IllegalArgumentException("core pattern variant has wrong payload arity");
        }
        List<PatternCoverage.Pattern> arguments = new ArrayList<>();
        for (int index = 0; index < variant.arguments().size(); index++) {
          CoreType payloadType =
              absolute(instance.definition(), declaration.fields().get(index).type())
                  .substitute(instance.type().arguments()::get);
          arguments.add(
              verifyPattern(owner, callable, variant.arguments().get(index), payloadType));
        }
        yield PatternCoverage.Pattern.constructor("variant:" + variant.variantKey(), arguments);
      }
      case CorePattern.Literal literal -> {
        CoreType literalType =
            switch (literal.value()) {
              case Integer ignored ->
                  literal.type().equals(CoreType.CODE_POINT)
                      ? CoreType.CODE_POINT
                      : CoreType.INTEGER;
              case Long ignored -> CoreType.LONG;
              case Float ignored -> CoreType.FLOAT;
              case Double ignored -> CoreType.DOUBLE;
              case Boolean ignored -> CoreType.BOOLEAN;
              case String ignored -> CoreType.STRING;
              default -> throw new IllegalArgumentException("unsupported core pattern literal");
            };
        requireSameType(literal.type(), literalType, "pattern literal representation");
        requireSameType(expected, literal.type(), "pattern literal");
        yield PatternCoverage.Pattern.constructor(literalKey(expected, literal.value()), List.of());
      }
      case CorePattern.Null ignored ->
          throw new IllegalArgumentException("core null pattern requires nullable type");
    };
  }

  private EnumInstance enumInstance(CoreType type) {
    if (!(nonNullable(type) instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("core variant pattern requires enum type");
    }
    DefinitionId definition = resolveExternal(user.definition());
    CoreDefinition target = program.definition(definition).orElseThrow();
    if (!(target instanceof CoreDefinition.Enum enumDeclaration)
        || declared.arguments().size() != enumDeclaration.typeParameters().size()) {
      throw new IllegalArgumentException("core variant pattern requires enum type");
    }
    return new EnumInstance(definition, enumDeclaration, declared);
  }

  private static String literalKey(CoreType expected, Object value) {
    if (isNumericLeaf(expected)) {
      return "numeric:" + expected + ":" + value;
    }
    if (expected.equals(CoreType.CODE_POINT)) return "codepoint:" + value;
    if (expected.equals(CoreType.BOOLEAN)) return "boolean:" + value;
    return "string:" + value;
  }

  private static boolean definitelyYields(CoreBlock block) {
    for (CoreStatement statement : block.statements()) {
      if (statement instanceof CoreStatement.ReturnStatement
          || statement instanceof CoreStatement.YieldStatement
          || statement instanceof CoreStatement.ThrowStatement) {
        return true;
      }
      if (statement instanceof CoreStatement.IfStatement conditional
          && definitelyYields(conditional.thenBlock())
          && definitelyYields(conditional.elseBlock())) {
        return true;
      }
      if (statement instanceof CoreStatement.TryStatement tried) {
        if (tried.finallyBlock().isPresent()
            && definitelyYields(tried.finallyBlock().orElseThrow())) {
          return true;
        }
        if (definitelyYields(tried.body())
            && tried.catches().stream().allMatch(clause -> definitelyYields(clause.body()))) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean definitelyExits(CoreBlock block) {
    for (CoreStatement statement : block.statements()) {
      if (statement instanceof CoreStatement.ReturnStatement
          || statement instanceof CoreStatement.ThrowStatement
          || statement instanceof CoreStatement.YieldStatement) {
        return true;
      }
      if (statement instanceof CoreStatement.IfStatement conditional
          && definitelyExits(conditional.thenBlock())
          && definitelyExits(conditional.elseBlock())) {
        return true;
      }
      if (statement instanceof CoreStatement.TryStatement tried) {
        if (tried.finallyBlock().isPresent()
            && definitelyExits(tried.finallyBlock().orElseThrow())) {
          return true;
        }
        if (definitelyExits(tried.body())
            && tried.catches().stream().allMatch(clause -> definitelyExits(clause.body()))) {
          return true;
        }
      }
    }
    return false;
  }

  private final class CorePatternDomain implements PatternCoverage.Domain<CoreType> {
    private final DefinitionId owner;

    private CorePatternDomain(DefinitionId owner) {
      this.owner = owner;
    }

    @Override
    public List<PatternCoverage.Constructor<CoreType>> constructors(CoreType type) {
      if (type.isNullable()) {
        return List.of(
            new PatternCoverage.Constructor<>("$null", List.of()),
            new PatternCoverage.Constructor<>("$value", List.of(nonNullable(type))));
      }
      if (type.equals(CoreType.BOOLEAN)) {
        return List.of(
            new PatternCoverage.Constructor<>("boolean:false", List.of()),
            new PatternCoverage.Constructor<>("boolean:true", List.of()));
      }
      CoreType absoluteType = absolute(owner, type);
      if (!(absoluteType instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
        return List.of();
      }
      DefinitionId definition = resolveExternal(user.definition());
      CoreDefinition target = program.definition(definition).orElseThrow();
      if (!(target instanceof CoreDefinition.Enum enumDeclaration)) return List.of();
      return enumDeclaration.variants().stream()
          .map(
              variant ->
                  new PatternCoverage.Constructor<>(
                      "variant:" + variant.key(),
                      variant.fields().stream()
                          .map(
                              field ->
                                  absolute(definition, field.type())
                                      .substitute(declared.arguments()::get))
                          .toList()))
          .toList();
    }

    @Override
    public PatternCoverage.Constructor<CoreType> openConstructor(CoreType type, String key) {
      return constructors(type).isEmpty()
          ? new PatternCoverage.Constructor<>(key, List.of())
          : null;
    }
  }

  private record EnumInstance(
      DefinitionId definition, CoreDefinition.Enum declaration, CoreType.Declared type) {}

  private enum ControlKind {
    LOOP,
    SWITCH
  }

  private static final class Control {
    private final ControlKind kind;
    private final CoreType yieldType;
    private LexicalLifetime referenceLifetime;

    private Control(ControlKind kind, CoreType yieldType) {
      this.kind = kind;
      this.yieldType = yieldType;
    }

    static Control loop() {
      return new Control(ControlKind.LOOP, CoreType.VOID);
    }

    static Control switched(CoreType type) {
      return new Control(ControlKind.SWITCH, type);
    }

    ControlKind kind() {
      return kind;
    }

    CoreType yieldType() {
      return yieldType;
    }

    LexicalLifetime referenceLifetime() {
      return referenceLifetime;
    }

    void mergeReferenceLifetime(LexicalLifetime lifetime) {
      referenceLifetime =
          referenceLifetime == null ? lifetime : referenceLifetime.narrowest(lifetime);
    }
  }

  private CoreType instantiatedFieldType(
      DefinitionId owner, CoreType receiverType, CoreFieldReference reference) {
    DefinitionId targetId = resolve(owner, reference.owner());
    CoreDefinition targetDefinition = program.definition(targetId).orElseThrow();
    List<CoreField> fields =
        switch (targetDefinition) {
          case CoreDefinition.Aggregate aggregate -> aggregate.fields();
          default -> throw new IllegalArgumentException("field owner or ordinal is invalid");
        };
    CoreField field =
        fields.stream()
            .filter(candidate -> candidate.ordinal() == reference.ordinal())
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("field owner or ordinal is invalid"));
    CoreType receiver = nonNullable(absolute(owner, receiverType));
    CoreDefinition.Aggregate target = (CoreDefinition.Aggregate) targetDefinition;
    CoreType.Declared declared = aggregateView(receiver, targetId);
    if (declared == null || declared.arguments().size() != target.typeParameters().size()) {
      throw new IllegalArgumentException("field receiver does not match its owner");
    }
    return absolute(targetId, field.type()).substitute(declared.arguments()::get);
  }

  private void verifyRuntimeType(
      DefinitionId owner, CoreDefinition.Callable callable, CoreRuntimeType runtimeType) {
    verifyRuntimeTypeTemplate(owner, runtimeType.template(), callable.reifiedTypeLocals().size());
    Set<Integer> parameters = new HashSet<>();
    collectTypeParameters(runtimeType.template(), parameters);
    Set<Integer> captures = new HashSet<>();
    for (CoreTypeCapture capture : runtimeType.captures()) {
      if (!captures.add(capture.typeParameterIndex())) {
        throw new IllegalArgumentException("runtime type captures must be unique");
      }
      if (capture.typeParameterIndex() >= callable.reifiedTypeLocals().size()
          || capture.localIndex() != callable.reifiedTypeLocals().get(capture.typeParameterIndex())
          || local(callable, capture.localIndex()).kind() != CoreLocal.Kind.REIFIED_TYPE) {
        throw new IllegalArgumentException("runtime type capture does not match a reified local");
      }
      referenceFlow.requireDeclared(capture.localIndex());
    }
    if (!captures.equals(parameters)) {
      throw new IllegalArgumentException("runtime type captures do not cover the template");
    }
  }

  private void verifyValueType(DefinitionId owner, CoreType type, int parameterCount) {
    verifyInhabitedType(owner, type, parameterCount, "core value ABI");
  }

  private void verifyReturnType(DefinitionId owner, CoreType type, int parameterCount) {
    if (type.equals(CoreType.VOID)) return;
    if (CoreTypes.containsReference(type)) {
      throw new IllegalArgumentException("core return ABI cannot contain a reference type");
    }
    verifyInhabitedType(owner, type, parameterCount, "core return ABI");
  }

  private void verifyParameterType(DefinitionId owner, CoreType type, int parameterCount) {
    if (CoreTypes.containsReference(type) && !(type instanceof CoreType.Reference)) {
      throw new IllegalArgumentException("core parameter ABI cannot contain a nested reference");
    }
    verifyValueType(owner, type, parameterCount);
  }

  private void verifyStoredType(DefinitionId owner, CoreType type, int parameterCount) {
    if (CoreTypes.containsReference(type)) {
      throw new IllegalArgumentException("stored core types cannot contain references");
    }
    verifyValueType(owner, type, parameterCount);
  }

  private void verifyRuntimeTypeTemplate(DefinitionId owner, CoreType type, int parameterCount) {
    if (CoreTypes.containsReference(type)) {
      throw new IllegalArgumentException("runtime type templates cannot contain references");
    }
    verifyInhabitedType(owner, type, parameterCount, "runtime type template");
  }

  private void verifyTypeParameters(
      DefinitionId owner, List<CoreTypeParameter> parameters, int parameterCount) {
    boolean defaultSeen = false;
    for (CoreTypeParameter parameter : parameters) {
      parameter
          .upperBound()
          .ifPresent(
              bound -> {
                verifyValueType(owner, bound, parameterCount);
                CoreType resolved = absolute(owner, bound);
                if (resolved.isNullable()) {
                  throw new IllegalArgumentException(
                      "type parameter bound must be a non-null class, interface, or type parameter");
                }
                if (nonNullable(resolved) instanceof CoreType.Parameter boundParameter) {
                  if (boundParameter.index() == parameter.index()) {
                    throw new IllegalArgumentException("cyclic type parameter bound");
                  }
                  return;
                }
                if (!(nonNullable(resolved) instanceof CoreType.Declared declared)
                    || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
                  throw new IllegalArgumentException(
                      "type parameter bound must be a non-null class, interface, or type parameter");
                }
                CoreDefinition definition =
                    program.definition(resolveExternal(user.definition())).orElseThrow();
                if (!(definition instanceof CoreDefinition.Interface)
                    && (!(definition instanceof CoreDefinition.Aggregate aggregate)
                        || aggregate.kind() != CoreAggregateKind.CLASS)) {
                  throw new IllegalArgumentException(
                      "type parameter bound must be a non-null class or interface");
                }
              });
      if (parameter.defaultType().isEmpty()) {
        if (defaultSeen) {
          throw new IllegalArgumentException(
              "required type parameter follows a default type parameter");
        }
        continue;
      }
      defaultSeen = true;
      CoreType defaultType = parameter.defaultType().orElseThrow();
      verifyValueType(owner, defaultType, parameterCount);
      Set<Integer> referenced = new HashSet<>();
      collectTypeParameters(defaultType, referenced);
      if (referenced.stream().anyMatch(index -> index >= parameter.index())) {
        throw new IllegalArgumentException(
            "type parameter default may reference earlier type parameters only");
      }
      if (parameter.upperBound().isPresent()) {
        CoreType bound = absolute(owner, parameter.upperBound().orElseThrow());
        CoreType actual = absolute(owner, defaultType);
        if (!isAssignableThroughTypeParameterBounds(bound, owner, actual, new HashSet<>())) {
          throw new IllegalArgumentException("type parameter default does not satisfy its bound");
        }
      }
    }
  }

  private void verifyTypeArgumentBounds(
      DefinitionId owner,
      List<CoreTypeParameter> parameters,
      List<CoreType> substitutions,
      DefinitionId actualOwner) {
    List<CoreType> absoluteSubstitutions =
        substitutions.stream().map(type -> absolute(actualOwner, type)).toList();
    for (CoreTypeParameter parameter : parameters) {
      if (parameter.upperBound().isEmpty()) continue;
      CoreType expected =
          absolute(owner, parameter.upperBound().orElseThrow())
              .substitute(absoluteSubstitutions::get);
      CoreType actual = absoluteSubstitutions.get(parameter.index());
      if (isAssignable(expected, actual)) continue;
      if (actual instanceof CoreType.Parameter actualParameter) {
        ParameterContext context = typeParameterContext(actualOwner, actualParameter.index());
        if (context != null && context.parameter().upperBound().isPresent()) {
          CoreType actualBound =
              absolute(context.owner(), context.parameter().upperBound().orElseThrow());
          if (isAssignable(expected, actualBound)) continue;
        }
      }
      requireAssignable(expected, actual, "type argument bound");
    }
  }

  private ParameterContext typeParameterContext(DefinitionId owner, int index) {
    CoreDefinition definition = program.definition(owner).orElseThrow();
    if (definition instanceof CoreDefinition.Callable callable) {
      if (index < callable.receiverTypeParameterCount()) {
        return receiverTypeParameterContext(owner, callable.receiverType().orElseThrow(), index);
      }
      return callable.typeParameters().stream()
          .filter(parameter -> parameter.index() == index)
          .findFirst()
          .map(parameter -> new ParameterContext(owner, parameter))
          .orElse(null);
    }
    if (definition instanceof CoreDefinition.InterfaceMethod method) {
      CoreType receiver = absolute(owner, method.receiverInterfaceType());
      int receiverCount =
          receiver instanceof CoreType.Declared declared ? declared.arguments().size() : 0;
      if (index < receiverCount) {
        return receiverTypeParameterContext(owner, method.receiverInterfaceType(), index);
      }
      return method.typeParameters().stream()
          .filter(parameter -> parameter.index() == index)
          .findFirst()
          .map(parameter -> new ParameterContext(owner, parameter))
          .orElse(null);
    }
    List<CoreTypeParameter> parameters =
        switch (definition) {
          case CoreDefinition.Aggregate aggregate -> aggregate.typeParameters();
          case CoreDefinition.Interface interfaceDefinition -> interfaceDefinition.typeParameters();
          case CoreDefinition.Enum enumDefinition -> enumDefinition.typeParameters();
          case CoreDefinition.BuiltinConformance conformance -> conformance.typeParameters();
          case CoreDefinition.Callable ignored -> throw new IllegalStateException();
          case CoreDefinition.InterfaceMethod ignored -> throw new IllegalStateException();
        };
    return parameters.stream()
        .filter(parameter -> parameter.index() == index)
        .findFirst()
        .map(parameter -> new ParameterContext(owner, parameter))
        .orElse(null);
  }

  private ParameterContext receiverTypeParameterContext(
      DefinitionId owner, CoreType receiverType, int index) {
    CoreType receiver = absolute(owner, receiverType);
    if (!(receiver instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      return null;
    }
    DefinitionId receiverOwner = resolveExternal(user.definition());
    CoreDefinition definition = program.definition(receiverOwner).orElseThrow();
    List<CoreTypeParameter> parameters =
        definition instanceof CoreDefinition.Aggregate aggregate
            ? aggregate.typeParameters()
            : definition instanceof CoreDefinition.Interface interfaceDefinition
                ? interfaceDefinition.typeParameters()
                : List.of();
    return parameters.stream()
        .filter(parameter -> parameter.index() == index)
        .findFirst()
        .map(parameter -> new ParameterContext(receiverOwner, parameter))
        .orElse(null);
  }

  private record ParameterContext(DefinitionId owner, CoreTypeParameter parameter) {}

  private void verifyLocalType(DefinitionId owner, CoreLocal local, int parameterCount) {
    if (local.kind() == CoreLocal.Kind.REIFIED_TYPE || local.kind() == CoreLocal.Kind.ITERATOR) {
      if (!local.type().equals(CoreType.DYNAMIC)) {
        throw new IllegalArgumentException("internal runtime locals require dynamic type");
      }
      return;
    }
    if (CoreTypes.containsReference(local.type())
        && !(local.type() instanceof CoreType.Reference)) {
      throw new IllegalArgumentException("core local ABI cannot contain a nested reference");
    }
    verifyValueType(owner, local.type(), parameterCount);
  }

  private void verifyInhabitedType(
      DefinitionId owner, CoreType type, int parameterCount, String subject) {
    switch (type) {
      case CoreType.Parameter parameter -> {
        if (parameter.index() >= parameterCount) {
          throw new IllegalArgumentException("core type parameter is outside its ABI");
        }
      }
      case CoreType.Declared declared -> {
        if (declared.arguments().stream().anyMatch(CoreTypes::containsReference)) {
          throw new IllegalArgumentException("declared core types cannot contain references");
        }
        boolean classLiteral =
            declared.constructor() instanceof CoreTypeConstructor.Builtin builtin
                && builtin.id().value().equals("std.core.Class");
        declared.arguments().stream()
            .filter(argument -> !argument.equals(CoreType.EXISTENTIAL))
            .filter(argument -> !classLiteral || !argument.equals(CoreType.VOID))
            .forEach(argument -> verifyInhabitedType(owner, argument, parameterCount, subject));
        switch (declared.constructor()) {
          case CoreTypeConstructor.Builtin builtin -> verifyBuiltinType(builtin, declared);
          case CoreTypeConstructor.User user -> verifyUserType(owner, user, declared);
        }
      }
      case CoreType.Function function -> {
        if (function.returnType().equals(CoreType.EXISTENTIAL)
            && function.parameterTypes().isEmpty()) {
          break;
        }
        if (CoreTypes.containsReference(function.returnType())
            || function.parameterTypes().stream().anyMatch(CoreTypes::containsReference)) {
          throw new IllegalArgumentException("function core types cannot contain references");
        }
        verifyReturnType(owner, function.returnType(), parameterCount);
        function
            .parameterTypes()
            .forEach(parameter -> verifyValueType(owner, parameter, parameterCount));
      }
      case CoreType.Reference reference -> {
        verifyValueType(owner, reference.target(), parameterCount);
        CoreType target = absolute(owner, reference.target());
        if (!(target instanceof CoreType.Declared declared)
            || declared.category() != CoreValueCategory.VALUE) {
          throw new IllegalArgumentException("reference target must be a value type");
        }
      }
      case CoreType.Special ignored ->
          throw new IllegalArgumentException(subject + " requires an inhabitable type");
    }
  }

  private void verifyBuiltinType(
      CoreTypeConstructor.Builtin constructor, CoreType.Declared declared) {
    String identity = constructor.id().value();
    String prefix = "std.core.";
    if (!identity.startsWith(prefix)) {
      throw new IllegalArgumentException("unknown builtin core type " + identity);
    }
    var definition =
        builtins
            .type(identity.substring(prefix.length()))
            .orElseThrow(
                () -> new IllegalArgumentException("unknown builtin core type " + identity));
    if (definition.arity() != declared.arguments().size()) {
      throw new IllegalArgumentException("builtin core type has the wrong arity");
    }
    CoreValueCategory expected =
        switch (definition.symbol().type().category()) {
          case IDENTITY -> CoreValueCategory.IDENTITY;
          case VALUE -> CoreValueCategory.VALUE;
          case POLYMORPHIC -> CoreValueCategory.POLYMORPHIC;
          case VOID, DYNAMIC ->
              throw new IllegalArgumentException("builtin core type cannot be inhabited");
        };
    if (declared.category() != expected) {
      throw new IllegalArgumentException("builtin core type has the wrong value category");
    }
  }

  private void verifyUserType(
      DefinitionId owner, CoreTypeConstructor.User constructor, CoreType.Declared declared) {
    DefinitionId targetId = resolve(owner, constructor.definition());
    CoreDefinition target = program.definition(targetId).orElseThrow();
    int arity;
    CoreValueCategory category;
    if (target instanceof CoreDefinition.Aggregate declaration) {
      arity = declaration.typeParameters().size();
      category = declaration.valueCategory();
    } else if (target instanceof CoreDefinition.Enum declaration) {
      arity = declaration.typeParameters().size();
      category = CoreValueCategory.VALUE;
    } else if (target instanceof CoreDefinition.Interface declaration) {
      arity = declaration.typeParameters().size();
      category = CoreValueCategory.POLYMORPHIC;
    } else {
      throw new IllegalArgumentException("declared core type target is not nominal");
    }
    if (declared.arguments().size() != arity || declared.category() != category) {
      throw new IllegalArgumentException(
          "declared core type does not match its nominal ABI: target="
              + targetId
              + ", expectedArity="
              + arity
              + ", actualArity="
              + declared.arguments().size()
              + ", expectedCategory="
              + category
              + ", actualCategory="
              + declared.category());
    }
    List<CoreTypeParameter> parameters =
        switch (target) {
          case CoreDefinition.Aggregate declaration -> declaration.typeParameters();
          case CoreDefinition.Enum declaration -> declaration.typeParameters();
          case CoreDefinition.Interface declaration -> declaration.typeParameters();
          default -> throw new IllegalStateException("nominal definition kind changed");
        };
    verifyTypeArgumentBounds(targetId, parameters, declared.arguments(), owner);
  }

  private CoreType declaredArgument(
      DefinitionId owner, CoreType type, String builtinIdentity, int index) {
    CoreType absolute = nonNullable(absolute(owner, type));
    if (!(absolute instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.Builtin builtin)
        || !builtin.id().value().equals(builtinIdentity)
        || index >= declared.arguments().size()) {
      throw new IllegalArgumentException("core expression has an incompatible declared type");
    }
    return declared.arguments().get(index);
  }

  private static void collectTypeParameters(CoreType type, Set<Integer> result) {
    switch (type) {
      case CoreType.Parameter parameter -> result.add(parameter.index());
      case CoreType.Declared declared ->
          declared.arguments().forEach(argument -> collectTypeParameters(argument, result));
      case CoreType.Function function -> {
        collectTypeParameters(function.returnType(), result);
        function.parameterTypes().forEach(argument -> collectTypeParameters(argument, result));
      }
      case CoreType.Reference reference -> collectTypeParameters(reference.target(), result);
      case CoreType.Special ignored -> {}
    }
  }

  private static void verifyDenseArguments(List<CoreArgument> arguments, int parameterCount) {
    if (!denseArguments(arguments, parameterCount)) {
      throw new IllegalArgumentException("core arguments do not match the target arity");
    }
  }

  private static boolean denseArguments(List<CoreArgument> arguments, int parameterCount) {
    if (arguments.size() != parameterCount) return false;
    boolean[] supplied = new boolean[parameterCount];
    for (CoreArgument argument : arguments) {
      if (argument.parameterIndex() >= parameterCount || supplied[argument.parameterIndex()]) {
        return false;
      }
      supplied[argument.parameterIndex()] = true;
    }
    return true;
  }

  private static CoreLocal local(CoreDefinition.Callable callable, int index) {
    if (index < 0 || index >= callable.locals().size()) {
      throw new IllegalArgumentException("core local use is outside the local table");
    }
    return callable.locals().get(index);
  }

  private DefinitionId resolve(DefinitionId owner, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalArgumentException("core program contains a pending reference");
    }
    return program.resolve(owner, reference);
  }

  private static DefinitionId resolveExternal(CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference.External external)) {
      throw new IllegalArgumentException("absolute core type contains a relative reference");
    }
    return external.definition();
  }

  private CoreType absolute(DefinitionId owner, CoreType type) {
    return CoreTypes.absolute(type, owner, program);
  }

  private void requireAssignable(
      DefinitionId expectedOwner,
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      String subject) {
    CoreType expectedType = absolute(expectedOwner, expected);
    CoreType actualType = absolute(actualOwner, actual);
    if (!isAssignableThroughTypeParameterBounds(
        expectedType, actualOwner, actualType, new HashSet<>())) {
      throw new IllegalArgumentException(
          subject
              + " type does not match its ABI: expected "
              + expectedType
              + ", actual "
              + actualType);
    }
  }

  private boolean isAssignableThroughTypeParameterBounds(
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      Set<Map.Entry<DefinitionId, Integer>> visiting) {
    if (isAssignable(expected, actual)) return true;
    if (actual.isNullable() && !expected.isNullable()) return false;
    if (!(nonNullable(actual) instanceof CoreType.Parameter parameter)) return false;
    Map.Entry<DefinitionId, Integer> key = Map.entry(actualOwner, parameter.index());
    if (!visiting.add(key)) return false;
    ParameterContext context = typeParameterContext(actualOwner, parameter.index());
    if (context == null || context.parameter().upperBound().isEmpty()) return false;
    CoreType bound = absolute(context.owner(), context.parameter().upperBound().orElseThrow());
    if (actual.isNullable()) bound = bound.asNullable();
    return isAssignableThroughTypeParameterBounds(expected, context.owner(), bound, visiting);
  }

  private void requireAssignable(CoreType expected, CoreType actual, String subject) {
    if (!isAssignable(expected, actual)) {
      throw new IllegalArgumentException(
          subject + " type does not match its ABI: expected " + expected + ", actual " + actual);
    }
  }

  private void requireSameType(
      DefinitionId expectedOwner,
      CoreType expected,
      DefinitionId actualOwner,
      CoreType actual,
      String subject) {
    requireSameType(absolute(expectedOwner, expected), absolute(actualOwner, actual), subject);
  }

  private static void requireSameType(CoreType expected, CoreType actual, String subject) {
    if (!expected.equals(actual)) {
      throw new IllegalArgumentException(subject + " type does not match its ABI");
    }
  }

  private boolean isAssignable(CoreType expected, CoreType actual) {
    if (expected.equals(CoreType.DYNAMIC) || actual.equals(CoreType.DYNAMIC)) return true;
    if (expected.equals(CoreType.EXISTENTIAL) || actual.equals(CoreType.EXISTENTIAL)) {
      return expected.equals(CoreType.EXISTENTIAL) && actual.equals(CoreType.EXISTENTIAL);
    }
    if (actual.equals(CoreType.NULL)) return expected.isNullable();
    if (expected.equals(CoreType.NULL)) return actual.equals(CoreType.NULL);
    CoreType expectedValue = nonNullable(expected);
    CoreType actualValue = nonNullable(actual);
    if (expectedValue.equals(CoreType.ANY)) return expected.isNullable() || !actual.isNullable();
    boolean compatible =
        matchesExistentialProjection(expectedValue, actualValue)
            || expectedValue.equals(CoreType.NUMBER) && isNumericLeaf(actualValue);
    if (!compatible
        && expectedValue instanceof CoreType.Declared expectedDeclared
        && expectedDeclared.constructor() instanceof CoreTypeConstructor.User expectedUser) {
      CoreType.Declared view =
          aggregateView(actualValue, resolveExternal(expectedUser.definition()));
      compatible = expectedValue.equals(view);
    }
    if (!compatible
        && expectedValue instanceof CoreType.Declared expectedDeclared
        && expectedDeclared.category() == CoreValueCategory.POLYMORPHIC
        && expectedDeclared.constructor() instanceof CoreTypeConstructor.User expectedUser) {
      DefinitionId expectedInterface = resolveExternal(expectedUser.definition());
      CoreType realized = realizedInterface(actualValue, expectedInterface);
      compatible = expectedValue.equals(realized);
    }
    return compatible && (expected.isNullable() || !actual.isNullable());
  }

  private static boolean matchesExistentialProjection(CoreType expected, CoreType actual) {
    if (expected.equals(actual) || expected.equals(CoreType.EXISTENTIAL)) return true;
    if (expected instanceof CoreType.Function expectedFunction
        && actual instanceof CoreType.Function actualFunction) {
      return expectedFunction.returnType().equals(CoreType.EXISTENTIAL)
          && expectedFunction.parameterTypes().isEmpty()
          && expectedFunction.nullability() == actualFunction.nullability();
    }
    if (!(expected instanceof CoreType.Declared expectedDeclared)
        || !(actual instanceof CoreType.Declared actualDeclared)
        || !expectedDeclared.constructor().equals(actualDeclared.constructor())
        || expectedDeclared.category() != actualDeclared.category()
        || expectedDeclared.arguments().size() != actualDeclared.arguments().size()) {
      return false;
    }
    for (int index = 0; index < expectedDeclared.arguments().size(); index++) {
      CoreType expectedArgument = expectedDeclared.arguments().get(index);
      CoreType actualArgument = actualDeclared.arguments().get(index);
      if (!expectedArgument.equals(CoreType.EXISTENTIAL)
          && !expectedArgument.equals(actualArgument)) {
        return false;
      }
    }
    return true;
  }

  private CoreType.Declared aggregateView(CoreType type, DefinitionId target) {
    CoreType current = nonNullable(type);
    Set<DefinitionId> visited = new HashSet<>();
    while (current instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user) {
      DefinitionId id = resolveExternal(user.definition());
      if (!visited.add(id)) return null;
      if (id.equals(target)) return declared;
      CoreDefinition definition = program.definition(id).orElse(null);
      if (!(definition instanceof CoreDefinition.Aggregate aggregate)
          || aggregate.parentType().isEmpty()) return null;
      current =
          absolute(id, aggregate.parentType().orElseThrow()).substitute(declared.arguments()::get);
    }
    return null;
  }

  private static boolean isNumericLeaf(CoreType type) {
    CoreType value = nonNullable(type);
    return value.equals(CoreType.INTEGER)
        || value.equals(CoreType.LONG)
        || value.equals(CoreType.FLOAT)
        || value.equals(CoreType.DOUBLE);
  }

  private CoreType realizedInterface(CoreType actual, DefinitionId target) {
    if (actual instanceof CoreType.Declared builtinActual
        && builtinActual.constructor() instanceof CoreTypeConstructor.Builtin) {
      for (CoreDefinitionRecord record : program.definitions()) {
        if (!(record.definition() instanceof CoreDefinition.BuiltinConformance conformance))
          continue;
        Map<Integer, CoreType> substitutions = new LinkedHashMap<>();
        if (!matchCoreTemplate(
            absolute(record.id(), conformance.concreteBuiltinType()),
            builtinActual,
            substitutions)) {
          continue;
        }
        CoreType interfaceType =
            absolute(record.id(), conformance.interfaceType()).substitute(substitutions::get);
        InterfaceInstance instance = interfaceInstance(record.id(), interfaceType);
        List<CoreType> arguments = interfaceSubstitutions(instance, target);
        if (arguments != null) return interfaceType(target, arguments);
      }
      return null;
    }
    if (!(actual instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      return null;
    }
    DefinitionId actualId = resolveExternal(user.definition());
    CoreDefinition definition = program.definition(actualId).orElseThrow();
    if (definition instanceof CoreDefinition.Interface interfaceDefinition) {
      List<CoreType> arguments =
          interfaceSubstitutions(
              new InterfaceInstance(actualId, interfaceDefinition, declared), target);
      if (arguments == null) return null;
      return interfaceType(target, arguments);
    }
    if (!(definition instanceof CoreDefinition.Aggregate aggregateDefinition)) return null;
    for (CoreConformance conformance : aggregateDefinition.conformances()) {
      CoreType instantiated =
          absolute(actualId, conformance.interfaceType()).substitute(declared.arguments()::get);
      InterfaceInstance instance = interfaceInstance(actualId, instantiated);
      List<CoreType> arguments = interfaceSubstitutions(instance, target);
      if (arguments != null) return interfaceType(target, arguments);
    }
    if (aggregateDefinition.parentType().isPresent()) {
      CoreType parent =
          absolute(actualId, aggregateDefinition.parentType().orElseThrow())
              .substitute(declared.arguments()::get);
      return realizedInterface(parent, target);
    }
    return null;
  }

  private static boolean matchCoreTemplate(
      CoreType pattern, CoreType actual, Map<Integer, CoreType> substitutions) {
    if (pattern instanceof CoreType.Parameter parameter) {
      CoreType value =
          parameter.nullability() == CoreNullability.NULLABLE ? nonNullable(actual) : actual;
      CoreType previous = substitutions.putIfAbsent(parameter.index(), value);
      return previous == null || previous.equals(value);
    }
    if (!(pattern instanceof CoreType.Declared expected)
        || !(actual instanceof CoreType.Declared found)
        || !expected.constructor().equals(found.constructor())
        || expected.category() != found.category()
        || expected.nullability() != found.nullability()
        || expected.arguments().size() != found.arguments().size()) {
      return pattern.equals(actual);
    }
    for (int index = 0; index < expected.arguments().size(); index++) {
      if (!matchCoreTemplate(
          expected.arguments().get(index), found.arguments().get(index), substitutions)) {
        return false;
      }
    }
    return true;
  }

  private static CoreType interfaceType(DefinitionId definition, List<CoreType> arguments) {
    return new CoreType.Declared(
        new CoreTypeConstructor.User(new DefinitionReference.External(definition)),
        arguments,
        CoreValueCategory.POLYMORPHIC,
        CoreNullability.NON_NULL);
  }

  private static CoreType safeResult(CoreType result, boolean nullSafe, CoreType receiver) {
    return nullSafe && receiver.isNullable() ? result.asNullable() : result;
  }

  private static boolean mayContainNull(CoreType type) {
    return type.isNullable() || type instanceof CoreType.Parameter;
  }

  private static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          declared.nullability() == CoreNullability.NON_NULL
              ? declared
              : new CoreType.Declared(
                  declared.constructor(),
                  declared.arguments(),
                  declared.category(),
                  CoreNullability.NON_NULL);
      case CoreType.Function function ->
          function.nullability() == CoreNullability.NON_NULL
              ? function
              : new CoreType.Function(
                  function.returnType(), function.parameterTypes(), CoreNullability.NON_NULL);
      case CoreType.Parameter parameter ->
          parameter.nullability() == CoreNullability.NON_NULL
              ? parameter
              : new CoreType.Parameter(parameter.index(), CoreNullability.NON_NULL);
      case CoreType.Reference reference -> reference;
      case CoreType.Special special -> special;
    };
  }
}
