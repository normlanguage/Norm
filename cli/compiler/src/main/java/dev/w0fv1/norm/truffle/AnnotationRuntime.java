package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreAggregateKind;
import dev.w0fv1.norm.core.CoreAnnotationApplication;
import dev.w0fv1.norm.core.CoreAnnotationPath;
import dev.w0fv1.norm.core.CoreAnnotationReference;
import dev.w0fv1.norm.core.CoreAnnotationReferenceSite;
import dev.w0fv1.norm.core.CoreAnnotationTarget;
import dev.w0fv1.norm.core.CoreAnnotationValue;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionLink;
import dev.w0fv1.norm.core.CoreDefinitionRecord;
import dev.w0fv1.norm.core.CoreField;
import dev.w0fv1.norm.core.CoreFieldInterceptorProtocol;
import dev.w0fv1.norm.core.CoreFunctionInterceptorProtocol;
import dev.w0fv1.norm.core.CoreInterceptor;
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreParameterInterceptorProtocol;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AnnotationRuntime {
  private final CallTarget fieldWriter;
  private final dev.w0fv1.norm.core.CoreTypeRelations typeRelations;
  private final RuntimeProgram program;
  private final Map<JarBindingClassReference.Nominal, CoreDefinitionRecord> nominalStructures;
  private final RuntimeDeclarationIndex declarations;
  private final Map<ApplicationKey, List<CoreAnnotationValue>> applications;
  private final CoreFunctionInterceptorProtocol functionInterceptor;
  private final CoreParameterInterceptorProtocol parameterInterceptor;
  private final CoreFieldInterceptorProtocol fieldInterceptor;
  private final SerializationRuntime serialization;
  private final ConfigurationRuntime configuration;
  private final MapperEngine mapper;
  private final XmlDataFormat xml;
  private Map<DefinitionId, RuntimeValues.AggregateInfo> aggregateInfo = Map.of();
  private Map<DefinitionId, CallTarget> callableTargets = Map.of();
  private final Map<DefinitionOccurrenceId, List<dev.w0fv1.norm.core.CoreBindingShape.Parameter>>
      callableParameters = new LinkedHashMap<>();
  private final Map<DefinitionOccurrenceId, dev.w0fv1.norm.core.CoreConstructorParameters>
      constructorParameters = new LinkedHashMap<>();

  private final dev.w0fv1.norm.core.CoreRuntimeIdentity identity;
  private final dev.w0fv1.norm.core.CoreAuthoringMap authoring;
  private final Map<ApplicationKey, dev.w0fv1.norm.core.CoreAnnotationReferenceSite>
      configurationSites = new LinkedHashMap<>();
  private Map<DefinitionOccurrenceId, CallTarget> declarationTargets = Map.of();
  private LiveBindings.Version version;
  private GuestValueFactory values;
  private final Map<ApplicationKey, RuntimeValues.ObjectValue> annotationViews =
      new LinkedHashMap<>();

  AnnotationRuntime(CoreArtifact artifact) {
    this(artifact, dev.w0fv1.norm.core.CoreRuntimeIdentity.create(artifact));
  }

  AnnotationRuntime(CoreArtifact artifact, dev.w0fv1.norm.core.CoreRuntimeIdentity identity) {
    this.identity = identity;
    this.authoring = artifact.authoring();
    for (var occurrence : artifact.authoring().occurrences()) {
      if (occurrence.role() == dev.w0fv1.norm.core.CoreDefinitionRole.CONSTRUCTOR) {
        constructorParameters.put(
            occurrence.id(),
            dev.w0fv1.norm.core.CoreConstructorParameters.forCallable(
                (CoreDefinition.Callable)
                    artifact.program().definition(occurrence.id().representative()).orElseThrow()));
      }
    }
    for (var binding : artifact.namespace().bindings()) {
      if (!binding.shape().parameters().isEmpty())
        callableParameters.put(binding.occurrence(), binding.shape().parameters());
    }
    program = RuntimeProgram.from(artifact, identity);
    Map<JarBindingClassReference.Nominal, CoreDefinitionRecord> indexedNominals =
        new LinkedHashMap<>();
    for (CoreDefinitionRecord record : program.structures()) {
      CoreNominalTypeKey nominal =
          switch (record.definition()) {
            case CoreDefinition.Aggregate aggregate -> aggregate.nominalType();
            case CoreDefinition.Enum declaration -> declaration.nominalType();
            case CoreDefinition.Interface declaration -> declaration.nominalType();
            default -> null;
          };
      if (nominal != null) {
        indexedNominals.putIfAbsent(
            new JarBindingClassReference.Nominal(
                nominal.module(), nominal.packageName(), nominal.name()),
            record);
      }
    }
    nominalStructures = Map.copyOf(indexedNominals);
    typeRelations = new dev.w0fv1.norm.core.CoreTypeRelations(identity.definitions());
    declarations = RuntimeDeclarationIndex.from(artifact.authoring());
    Map<ApplicationKey, List<CoreAnnotationValue>> indexed = new LinkedHashMap<>();
    for (CoreAnnotationApplication application : artifact.metadata().annotations()) {
      ApplicationKey key = key(application);
      if (key != null) {
        indexed.put(key, application.values().stream().map(identity::annotationValue).toList());
        configurationSites.put(
            key,
            new dev.w0fv1.norm.core.CoreAnnotationReferenceSite(
                new dev.w0fv1.norm.core.CoreAnnotationReferenceSite.Metadata(application.id()),
                dev.w0fv1.norm.core.CoreAnnotationPath.ROOT));
      }
    }
    applications = Map.copyOf(indexed);
    functionInterceptor = CoreFunctionInterceptorProtocol.resolve(artifact.program()).orElse(null);
    parameterInterceptor =
        CoreParameterInterceptorProtocol.resolve(artifact.program()).orElse(null);
    fieldInterceptor = CoreFieldInterceptorProtocol.resolve(artifact.program()).orElse(null);
    serialization = new SerializationRuntime(this);
    configuration = new ConfigurationRuntime(serialization);
    mapper = new MapperEngine(serialization);
    xml = new XmlDataFormat(serialization);
    fieldWriter =
        new com.oracle.truffle.api.nodes.RootNode(null) {
          @Child private FieldWriteNode writer = new FieldWriteNode();

          @Override
          public Object execute(com.oracle.truffle.api.frame.VirtualFrame frame) {
            Object[] arguments = frame.getArguments();
            writer.execute(
                (RuntimeValues.ObjectValue) arguments[1],
                (RuntimeValues.FieldKey) arguments[2],
                arguments[3],
                (ExecutionState) arguments[0],
                (RuntimeValues.AggregateInfo) arguments[4]);
            return null;
          }
        }.getCallTarget();
  }

  void initialize(
      Map<DefinitionOccurrenceId, RuntimeValues.AggregateInfo> occurrences,
      Map<DefinitionOccurrenceId, CallTarget> declarationTargets) {
    Map<DefinitionId, RuntimeValues.AggregateInfo> indexed = new LinkedHashMap<>();
    occurrences.forEach(
        (occurrence, info) -> indexed.putIfAbsent(identity.id(occurrence.representative()), info));
    aggregateInfo = Map.copyOf(indexed);
    this.declarationTargets = Map.copyOf(declarationTargets);
    var targets = new LinkedHashMap<DefinitionId, CallTarget>();
    var ambiguous = new java.util.HashSet<DefinitionId>();
    declarationTargets.forEach(
        (occurrence, target) -> {
          var id = occurrence.representative();
          if (targets.putIfAbsent(id, target) != null) ambiguous.add(id);
        });
    ambiguous.forEach(targets::remove);
    this.callableTargets = Map.copyOf(targets);
  }

  void attachVersion(LiveBindings.Version version) {
    this.version = version;
  }

  LiveBindings.Version version() {
    return version;
  }

  <T> T retain(T value) {
    if (version != null && version.revision() != null) version.revision().retain(value);
    return value;
  }

  void initializeValues(GuestValueFactory values) {
    this.values = java.util.Objects.requireNonNull(values);
  }

  GuestValueFactory values() {
    return java.util.Objects.requireNonNull(values);
  }

  AnnotationRuntime forType(CoreType type, AnnotationRuntime retained) {
    if (type instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user
        && program.structure(resolveExternal(user.definition())).isEmpty()) return retained;
    return this;
  }

  String name(CoreType type) {
    return displayName(type);
  }

  JarBindingClassReference jarClassReference(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          switch (declared.constructor()) {
            case CoreTypeConstructor.Builtin builtin ->
                new JarBindingClassReference.Builtin(builtin.id().value());
            case CoreTypeConstructor.User user -> {
              DefinitionId id = resolveExternal(user.definition());
              CoreNominalTypeKey nominal = nominalType(program.structure(id).orElseThrow());
              yield new JarBindingClassReference.Nominal(
                  nominal.module(), nominal.packageName(), nominal.name());
            }
          };
      case CoreType.Function ignored ->
          throw new IllegalArgumentException("Norm type has no Java class mapping: " + type);
      case CoreType.Parameter ignored ->
          throw new IllegalArgumentException("Norm type has no Java class mapping: " + type);
      case CoreType.Reference ignored ->
          throw new IllegalArgumentException("Norm type has no Java class mapping: " + type);
      case CoreType.Special ignored ->
          throw new IllegalArgumentException("Norm type has no Java class mapping: " + type);
    };
  }

  RuntimeValues.ClassValue jarClassValue(
      CoreType expectedType, List<JarBindingClassReference> candidates) {
    var classContract =
        dev.w0fv1.norm.abi.BuiltinContracts.standard().type("Class").orElseThrow().symbol().type();
    var classConstructor =
        new CoreTypeConstructor.Builtin(new BuiltinTypeId(classContract.identity()));
    CoreType expectedReflected =
        expectedType instanceof CoreType.Declared declared
                && declared.constructor().equals(classConstructor)
                && declared.arguments().size() == 1
            ? declared.arguments().getFirst()
            : CoreType.ANY;
    List<JarBindingClassReference> expectedMatches =
        candidates.stream().filter(candidate -> matches(expectedReflected, candidate)).toList();
    CoreType reflected;
    if (!expectedMatches.isEmpty()) {
      reflected = expectedReflected;
    } else if (candidates.size() == 1) {
      reflected = reflectedType(candidates.getFirst());
    } else {
      throw new IllegalArgumentException(
          "Java class maps to multiple Norm declarations: " + candidates);
    }
    CoreType resultType =
        new CoreType.Declared(
            classConstructor,
            List.of(reflected),
            CoreValueCategory.valueOf(classContract.category().name()),
            CoreNullability.NON_NULL);
    return new RuntimeValues.ClassValue(resultType, reflected, this);
  }

  CoreType jarReferenceType(
      CoreType expectedType, List<JarBindingClassReference.Nominal> candidates) {
    CoreType.Declared expected = declared(program.type(expectedType));
    DefinitionId expectedId =
        expected.constructor() instanceof CoreTypeConstructor.User expectedUser
            ? resolveExternal(expectedUser.definition())
            : null;
    for (JarBindingClassReference.Nominal candidate : candidates) {
      CoreDefinitionRecord record = nominalStructures.get(candidate);
      if (record == null
          || !(record.definition() instanceof CoreDefinition.Aggregate
              || record.definition() instanceof CoreDefinition.Enum)) {
        continue;
      }
      DefinitionId runtimeDefinition = record.id();
      CoreDefinition runtimeAggregate = program.structure(runtimeDefinition).orElseThrow();
      List<CoreType> arguments =
          typeParameterCount(runtimeAggregate) == expected.arguments().size()
              ? expected.arguments()
              : java.util.Collections.nCopies(
                  typeParameterCount(runtimeAggregate), CoreType.EXISTENTIAL);
      var projected =
          new CoreType.Declared(
              new CoreTypeConstructor.User(new DefinitionReference.External(runtimeDefinition)),
              arguments,
              valueCategory(runtimeAggregate),
              CoreNullability.NON_NULL);
      if (typeRelations.isAssignable(expected, projected)) return projected;
    }
    for (JarBindingClassReference.Nominal candidate : candidates) {
      CoreDefinitionRecord record = nominalStructures.get(candidate);
      if (record != null
          && record.id().equals(expectedId)
          && record.definition() instanceof CoreDefinition.Interface) {
        return new CoreType.Declared(
            expected.constructor(),
            expected.arguments(),
            expected.category(),
            CoreNullability.NON_NULL);
      }
    }
    throw new IllegalArgumentException(
        "Java reference has no concrete Norm implementation for " + expectedType);
  }

  RuntimeValues.ListValue fields(CoreType reflectedType, CoreType listType) {
    CoreType.Declared reflected = declared(reflectedType);
    if (!(reflected.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("fields require an aggregate type");
    }
    DefinitionId aggregateId = resolveExternal(user.definition());
    if (!(program.structure(aggregateId).orElseThrow()
            instanceof CoreDefinition.Aggregate aggregate)
        || aggregate.kind() == CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("fields require a class or value type");
    }
    RuntimeValues.AggregateInfo info = aggregateInfo.get(aggregateId);
    if (info == null) throw new IllegalStateException("aggregate reflection is not initialized");
    CoreType.Declared list = declared(listType);
    CoreType fieldRuntimeType = list.arguments().getFirst();
    List<Object> fields =
        info.fields().stream()
            .map(
                field ->
                    new RuntimeValues.FieldValue(
                        fieldRuntimeType,
                        reflectedType,
                        field.owner(),
                        field.name(),
                        field.index(),
                        reflectedFieldType(field, reflected),
                        this))
            .map(Object.class::cast)
            .toList();
    return new RuntimeValues.ListValue(listType, fields);
  }

  RuntimeValues.FieldValue field(CoreType fieldType, int ordinal) {
    CoreType.Declared descriptor = declared(fieldType);
    if (descriptor.arguments().size() != 2) {
      throw new IllegalArgumentException("field descriptor type is invalid");
    }
    CoreType ownerType = descriptor.arguments().getFirst();
    CoreType.Declared owner = declared(ownerType);
    if (!(owner.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("field owner must be an aggregate type");
    }
    DefinitionId aggregateId = resolveExternal(user.definition());
    RuntimeValues.AggregateInfo info = aggregateInfo.get(aggregateId);
    if (info == null || ordinal < 0 || ordinal >= info.fields().size()) {
      throw new IllegalArgumentException("field declaration is absent");
    }
    RuntimeValues.FieldPlan plan = info.fields().get(ordinal);
    return new RuntimeValues.FieldValue(
        fieldType,
        ownerType,
        plan.owner(),
        plan.name(),
        ordinal,
        reflectedFieldType(plan, owner),
        this);
  }

  @TruffleBoundary
  RuntimeValues.FieldValue field(
      RuntimeValues.FieldPlan plan, CoreType ownerType, CoreType fieldType) {
    CoreType.Declared owner = declared(ownerType);
    return new RuntimeValues.FieldValue(
        fieldType,
        ownerType,
        plan.owner(),
        plan.name(),
        plan.index(),
        reflectedFieldType(plan, owner),
        this);
  }

  RuntimeValues.FieldValue field(
      CoreType descriptorType, CoreAnnotationReference.FieldReference reference) {
    CoreType.Declared owner = declared(reference.ownerType());
    if (!(owner.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("field owner must be an aggregate type");
    }
    RuntimeValues.AggregateInfo info = aggregateInfo.get(resolveExternal(user.definition()));
    if (info == null || reference.ordinal() < 0 || reference.ordinal() >= info.fields().size()) {
      throw new IllegalArgumentException("field declaration is absent");
    }
    RuntimeValues.FieldPlan plan = info.fields().get(reference.ordinal());
    return new RuntimeValues.FieldValue(
        descriptorType,
        reference.ownerType(),
        plan.owner(),
        plan.name(),
        plan.index(),
        reference.valueType(),
        this);
  }

  RuntimeValues.ListValue functions(CoreType reflectedType, CoreType listType) {
    CoreType.Declared reflected = declared(reflectedType);
    DefinitionId aggregateId = aggregateDefinition(reflected, "functions");
    CoreDefinition.Aggregate aggregate =
        (CoreDefinition.Aggregate) program.structure(aggregateId).orElseThrow();
    RuntimeValues.AggregateInfo info = aggregateInfo.get(aggregateId);
    if (info == null) throw new IllegalStateException("aggregate reflection is not initialized");
    List<Object> functions =
        aggregate.dispatch().stream()
            .map(dispatch -> resolve(aggregateId, dispatch.slot()))
            .distinct()
            .map(
                slot -> {
                  RuntimeValues.DispatchTarget target = info.dispatch().get(identity.slot(slot));
                  if (!(target instanceof RuntimeValues.DispatchTarget.Callable callable)) {
                    throw new IllegalStateException("reflected function target is absent");
                  }
                  return (Object)
                      new RuntimeValues.Closure(
                          callable.target(),
                          occurrence(slot),
                          identity.slot(slot),
                          true,
                          null,
                          new Object[0],
                          reflected.arguments().toArray(),
                          new Object[0],
                          this);
                })
            .toList();
    return new RuntimeValues.ListValue(listType, functions);
  }

  RuntimeValues.ListValue constructors(CoreType reflectedType, CoreType listType) {
    CoreType.Declared reflected = declared(reflectedType);
    DefinitionId aggregateId = aggregateDefinition(reflected, "constructors");
    CoreDefinition.Aggregate aggregate =
        (CoreDefinition.Aggregate) program.structure(aggregateId).orElseThrow();
    CoreType descriptorType = declared(listType).arguments().getFirst();
    List<Object> constructors =
        aggregate.constructors().stream()
            .map(link -> resolve(aggregateId, link))
            .map(
                constructor ->
                    (Object)
                        new RuntimeValues.ConstructorValue(
                            descriptorType, reflectedType, occurrence(constructor), this))
            .toList();
    return new RuntimeValues.ListValue(listType, constructors);
  }

  String functionName(RuntimeValues.Closure function) {
    return declarations.name(function.declaration());
  }

  Object functionOwner(RuntimeValues.Closure function, CoreType classType) {
    RuntimeProgram.Callable callable = callable(function);
    if (callable.receiverType().isEmpty()) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    CoreType owner =
        CoreTypes.absolute(
                callable.receiverType().orElseThrow(),
                function.declaration().representative(),
                program)
            .substitute(
                index -> {
                  if (index >= function.receiverTypeArguments().length) {
                    return new CoreType.Parameter(index, CoreNullability.NON_NULL);
                  }
                  return (CoreType) function.receiverTypeArguments()[index];
                });
    return new RuntimeValues.ClassValue(classType, owner, this);
  }

  RuntimeValues.ListValue parameters(RuntimeValues.Closure function, CoreType listType) {
    CoreType descriptorType = declared(listType).arguments().getFirst();
    RuntimeProgram.Callable callable = callable(function);
    List<Object> parameters = new ArrayList<>();
    if (function.unbound() && callable.receiverType().isPresent()) {
      CoreType receiverType =
          CoreTypes.absolute(
                  callable.receiverType().orElseThrow(),
                  function.declaration().representative(),
                  program)
              .substitute(typeIndex -> callableTypeArgument(function, typeIndex));
      parameters.add(
          new RuntimeValues.ParameterValue(descriptorType, function, "this", receiverType, this));
    }
    java.util.stream.IntStream.range(0, callable.parameters().size())
        .mapToObj(index -> (Object) parameter(function, index, descriptorType))
        .forEach(parameters::add);
    return new RuntimeValues.ListValue(listType, parameters);
  }

  RuntimeValues.ParameterValue parameter(
      RuntimeValues.Closure function, int index, CoreType parameterType) {
    RuntimeProgram.Callable callable = callable(function);
    if (index < 0 || index >= callable.parameters().size()) {
      throw new IllegalArgumentException("parameter declaration is absent");
    }
    RuntimeProgram.Parameter parameter = callable.parameters().get(index);
    CoreType valueType =
        CoreTypes.absolute(parameter.type(), function.declaration().representative(), program)
            .substitute(typeIndex -> callableTypeArgument(function, typeIndex));
    return new RuntimeValues.ParameterValue(
        parameterType, function, parameter.name(), valueType, this);
  }

  RuntimeProgram.Callable callable(RuntimeValues.Closure function) {
    return program
        .callable(function.declaration().representative())
        .orElseThrow(() -> new IllegalArgumentException("function declaration is not callable"));
  }

  dev.w0fv1.norm.core.CoreConstructorParameters constructorParameters(DefinitionId slot) {
    var matching =
        constructorParameters.entrySet().stream()
            .filter(entry -> identity.slot(entry.getKey()).equals(slot))
            .toList();
    if (matching.size() != 1) {
      throw new IllegalArgumentException("Java application constructor declaration is not unique");
    }
    return matching.getFirst().getValue();
  }

  java.util.Optional<dev.w0fv1.norm.core.CoreConstructorParameters> constructorParameters(
      DefinitionOccurrenceId occurrence) {
    return java.util.Optional.ofNullable(constructorParameters.get(occurrence));
  }

  java.util.Optional<DefinitionOccurrenceId> parameterDefault(
      RuntimeValues.Closure operation, int index) {
    var parameters = callableParameters.get(operation.declaration());
    if (parameters == null) return java.util.Optional.empty();
    return parameters
        .get(index)
        .defaultValue()
        .map(value -> ((dev.w0fv1.norm.core.CoreDefaultArgument.Resolved) value).occurrence());
  }

  Object defaultArgument(RuntimeValues.Closure operation, int index, ExecutionState execution) {
    var target = parameterDefault(operation, index).orElseThrow();
    var arguments = new ArrayList<Object>();
    arguments.add(execution);
    if (operation.receiver() != null) arguments.add(operation.receiver());
    java.util.Collections.addAll(arguments, operation.receiverTypeArguments());
    java.util.Collections.addAll(arguments, operation.reifiedArguments());
    return declarationTargets.get(target).call(arguments.toArray());
  }

  CoreType callableTypeArgument(RuntimeValues.Closure function, int index) {
    if (index < function.receiverTypeArguments().length) {
      return (CoreType) function.receiverTypeArguments()[index];
    }
    int callableIndex = index - function.receiverTypeArguments().length;
    if (callableIndex < function.reifiedArguments().length) {
      return (CoreType) function.reifiedArguments()[callableIndex];
    }
    return new CoreType.Parameter(index, CoreNullability.NON_NULL);
  }

  Object fieldAnnotation(
      RuntimeValues.FieldValue field, CoreType annotationType, ExecutionState execution) {
    CoreType.Declared annotation = declared(annotationType);
    if (!(annotation.constructor() instanceof CoreTypeConstructor.User annotationUser)) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    DefinitionId annotationId = resolveExternal(annotationUser.definition());
    ApplicationKey key =
        new ApplicationKey(
            annotationId,
            AnnotationTarget.FIELD,
            new RuntimeValues.FieldKey(identity.id(field.owner().representative()), field.name()));
    List<CoreAnnotationValue> values = applications.get(key);
    if (values == null || retention(annotationId) != AnnotationRetention.RUNTIME) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    return execution.annotationExecution().value(this, key, values, execution);
  }

  Object readField(RuntimeValues.FieldValue field, Object receiver, ExecutionState execution) {
    var object = fieldReceiver(field, receiver);
    var view = field.annotations().aggregateInfo(object.objectInfo.definition());
    object.ensureLayout(view, execution);
    return RuntimeValues.copy(object.readField(field.key(), view, execution));
  }

  boolean hasFieldAnnotation(RuntimeValues.FieldValue field, CoreType annotationType) {
    if (!(annotationType instanceof CoreType.Declared requested)
        || !(requested.constructor() instanceof CoreTypeConstructor.User requestedUser))
      return false;
    DefinitionId requestedId = resolveExternal(requestedUser.definition());
    Object target =
        new RuntimeValues.FieldKey(identity.id(field.owner().representative()), field.name());
    for (ApplicationKey key : applications.keySet()) {
      if (key.target() != AnnotationTarget.FIELD
          || !key.definition().equals(target)
          || retention(key.annotation()) != AnnotationRetention.RUNTIME) continue;
      CoreType actual =
          new CoreType.Declared(
              new CoreTypeConstructor.User(new DefinitionReference.External(key.annotation())),
              List.of(),
              CoreValueCategory.IDENTITY,
              CoreNullability.NON_NULL);
      CoreType.Declared view = aggregateView(actual, requestedId);
      if (view != null && view.equals(requested)) return true;
    }
    return false;
  }

  RuntimeValues.OpaqueValue fieldIdentity(
      RuntimeValues.FieldValue field, Object receiver, CoreType type, ExecutionState execution) {
    Object value = readField(field, receiver, execution);
    if (value == null || value == RuntimeValues.NullValue.INSTANCE) {
      throw new IllegalArgumentException("field identity requires a non-null value");
    }
    return new RuntimeValues.OpaqueValue(
        type,
        new FieldIdentityKey(
            field.ownerType(),
            field.owner(),
            field.name(),
            new RuntimeValues.RuntimeKey(RuntimeValues.copy(value))),
        "FieldIdentity");
  }

  private record FieldIdentityKey(
      CoreType ownerType,
      DefinitionOccurrenceId owner,
      String field,
      RuntimeValues.RuntimeKey value) {}

  RuntimeValues.OpaqueValue bindField(
      RuntimeValues.FieldValue field, Object receiver, CoreType type) {
    RuntimeValues.ObjectValue object = fieldReceiver(field, receiver);
    if (((CoreType.Declared) object.type).category() != CoreValueCategory.IDENTITY) {
      throw new IllegalArgumentException("field binding requires a mutable class target");
    }
    return new RuntimeValues.OpaqueValue(type, new FieldHandle(field, object), "FieldHandle");
  }

  record FieldHandle(RuntimeValues.FieldValue field, RuntimeValues.ObjectValue receiver) {
    Object read(ExecutionState execution) {
      return field.annotations().readField(field, receiver, execution);
    }

    void write(Object value, ExecutionState execution) {
      field.annotations().writeField(field, receiver, value, execution);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof FieldHandle handle
          && receiver == handle.receiver
          && field.owner().equals(handle.field.owner())
          && field.name().equals(handle.field.name());
    }

    @Override
    public int hashCode() {
      return 31 * System.identityHashCode(receiver)
          + java.util.Objects.hash(field.owner(), field.name());
    }
  }

  boolean publicField(RuntimeValues.FieldValue field) {
    return aggregateInfo.get(field.owner().representative()).fields().stream()
        .filter(plan -> plan.name().equals(field.name()))
        .findFirst()
        .orElseThrow()
        .publicField();
  }

  void copyField(
      RuntimeValues.FieldValue field, Object target, Object source, ExecutionState execution) {
    writeField(field, target, readField(field, source, execution), execution);
  }

  void writeField(
      RuntimeValues.FieldValue field, Object target, Object value, ExecutionState execution) {
    RuntimeValues.ObjectValue object = fieldReceiver(field, target);
    if (((CoreType.Declared) object.type).category() != CoreValueCategory.IDENTITY) {
      throw new IllegalArgumentException("field writing requires a mutable class target");
    }
    if (value == RuntimeValues.NullValue.INSTANCE
        ? !field.fieldType().isNullable()
        : !typeRelations.isAssignable(field.fieldType(), RuntimeValues.runtimeType(value))) {
      throw new IllegalArgumentException("field value does not match its declared type");
    }
    var view = field.annotations().aggregateInfo(object.objectInfo.definition());
    try (var lease =
        execution.liveBindings() == null ? null : execution.liveBindings().stateWrite()) {
      fieldWriter.call(execution, object, field.key(), value, view);
    }
  }

  private RuntimeValues.ObjectValue fieldReceiver(RuntimeValues.FieldValue field, Object receiver) {
    if (!(receiver instanceof RuntimeValues.ObjectValue object)) {
      throw new IllegalArgumentException("field receiver does not match its declaring type");
    }
    CoreType.Declared actual = declared(object.type);
    CoreType.Declared expected = declared(field.ownerType());
    DefinitionId expectedId =
        resolveExternal(((CoreTypeConstructor.User) expected.constructor()).definition());
    CoreType.Declared view = aggregateView(actual, expectedId);
    if (view == null || !view.equals(expected)) {
      throw new IllegalArgumentException("field receiver does not match its declaring type");
    }
    return object;
  }

  Execution execution() {
    return new Execution();
  }

  SerializationRuntime serialization() {
    return serialization;
  }

  ConfigurationRuntime configuration() {
    return configuration;
  }

  MapperEngine mapper() {
    return mapper;
  }

  XmlDataFormat xml() {
    return xml;
  }

  RuntimeProgram program() {
    return program;
  }

  boolean isAssignable(CoreType expected, CoreType actual) {
    return typeRelations.isAssignable(expected, actual);
  }

  RuntimeValues.AggregateInfo aggregateInfo(DefinitionId definition) {
    return aggregateInfo.get(identity.id(definition));
  }

  List<CoreAnnotationValue> typeAnnotationValues(DefinitionId annotation, DefinitionId type) {
    TypeAnnotation found = typeAnnotation(annotation, type);
    return found == null ? null : found.values();
  }

  List<CoreAnnotationValue> fieldAnnotationValues(
      DefinitionId annotation, DefinitionOccurrenceId owner, int ordinal) {
    return applications.get(
        new ApplicationKey(annotation, AnnotationTarget.FIELD, fieldKey(owner, ordinal)));
  }

  Object annotation(CoreType reflectedType, CoreType annotationType, ExecutionState execution) {
    CoreType.Declared reflected = declared(reflectedType);
    if (!(reflected.constructor() instanceof CoreTypeConstructor.User reflectedUser)) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    CoreType.Declared annotation = declared(annotationType);
    if (!(annotation.constructor() instanceof CoreTypeConstructor.User annotationUser)) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    DefinitionId annotationId = resolveExternal(annotationUser.definition());
    TypeAnnotation found =
        typeAnnotation(annotationId, resolveExternal(reflectedUser.definition()));
    if (found == null || retention(annotationId) != AnnotationRetention.RUNTIME) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    return execution.annotationExecution().value(this, found.key(), found.values(), execution);
  }

  Object callableAnnotation(
      RuntimeValues.Closure function,
      int parameter,
      CoreType annotationType,
      ExecutionState execution) {
    CoreType.Declared annotation = declared(annotationType);
    if (!(annotation.constructor() instanceof CoreTypeConstructor.User user))
      return RuntimeValues.NullValue.INSTANCE;
    DefinitionId id = resolveExternal(user.definition());
    if (retention(id) != AnnotationRetention.RUNTIME) return RuntimeValues.NullValue.INSTANCE;
    ApplicationKey key =
        parameter < 0
            ? new ApplicationKey(
                id,
                constructorParameters.containsKey(function.declaration())
                    ? AnnotationTarget.CONSTRUCTOR
                    : AnnotationTarget.FUNCTION,
                identity.key(function.declaration()))
            : new ApplicationKey(
                id,
                AnnotationTarget.PARAMETER,
                new IndexedKey(identity.key(function.declaration()), parameter));
    var values = applications.get(key);
    return values == null
        ? RuntimeValues.NullValue.INSTANCE
        : execution.annotationExecution().value(this, key, values, execution);
  }

  private TypeAnnotation typeAnnotation(DefinitionId annotationId, DefinitionId typeId) {
    boolean inherited = program.annotationPolicy(annotationId).inherited();
    DefinitionId current = typeId;
    Set<DefinitionId> visited = new HashSet<>();
    while (visited.add(current)) {
      ApplicationKey key = new ApplicationKey(annotationId, AnnotationTarget.TYPE, current);
      List<CoreAnnotationValue> values = applications.get(key);
      if (values != null) return new TypeAnnotation(key, values);
      if (!inherited) return null;
      CoreDefinition definition = program.structure(current).orElse(null);
      if (!(definition instanceof CoreDefinition.Aggregate aggregate)
          || aggregate.parentType().isEmpty()) return null;
      CoreType parent = CoreTypes.absolute(aggregate.parentType().orElseThrow(), current, program);
      if (!(parent instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)) return null;
      current = resolveExternal(user.definition());
    }
    return null;
  }

  RuntimeValues.ObjectValue functionAnnotation(
      DefinitionOccurrenceId callable,
      int interceptorIndex,
      CoreInterceptor interceptor,
      ExecutionState execution) {
    DefinitionId annotationId = resolve(callable.representative(), interceptor.annotation());
    return behaviorAnnotation(
        new ApplicationKey(
            annotationId,
            constructorParameters.containsKey(callable)
                ? AnnotationTarget.CONSTRUCTOR
                : AnnotationTarget.FUNCTION,
            identity.key(callable)),
        interceptor,
        new CoreAnnotationReferenceSite(
            new CoreAnnotationReferenceSite.Definition(callable),
            CoreAnnotationPath.ROOT.child(CoreAnnotationPath.Kind.INTERCEPTOR, interceptorIndex)),
        execution);
  }

  RuntimeValues.ObjectValue parameterAnnotation(
      DefinitionOccurrenceId callable,
      int parameterIndex,
      int interceptorIndex,
      CoreInterceptor interceptor,
      ExecutionState execution) {
    DefinitionId annotationId = resolve(callable.representative(), interceptor.annotation());
    return behaviorAnnotation(
        new ApplicationKey(
            annotationId,
            AnnotationTarget.PARAMETER,
            new IndexedKey(identity.key(callable), parameterIndex)),
        interceptor,
        new CoreAnnotationReferenceSite(
            new CoreAnnotationReferenceSite.Definition(callable),
            CoreAnnotationPath.ROOT
                .child(CoreAnnotationPath.Kind.PARAMETER, parameterIndex)
                .child(CoreAnnotationPath.Kind.INTERCEPTOR, interceptorIndex)),
        execution);
  }

  RuntimeValues.ObjectValue fieldAnnotation(
      DefinitionOccurrenceId aggregate,
      int fieldIndex,
      int interceptorIndex,
      CoreInterceptor interceptor,
      ExecutionState execution) {
    DefinitionId annotationId = resolve(aggregate.representative(), interceptor.annotation());
    return behaviorAnnotation(
        new ApplicationKey(annotationId, AnnotationTarget.FIELD, fieldKey(aggregate, fieldIndex)),
        interceptor,
        new CoreAnnotationReferenceSite(
            new CoreAnnotationReferenceSite.Definition(
                new DefinitionOccurrenceId(
                    identity.source(aggregate.representative()), aggregate.ordinal())),
            CoreAnnotationPath.ROOT
                .child(CoreAnnotationPath.Kind.FIELD, fieldIndex)
                .child(CoreAnnotationPath.Kind.INTERCEPTOR, interceptorIndex)),
        execution);
  }

  private RuntimeValues.ObjectValue behaviorAnnotation(
      ApplicationKey key,
      CoreInterceptor interceptor,
      CoreAnnotationReferenceSite sourceSite,
      ExecutionState execution) {
    var configured = interceptor.values().stream().map(identity::annotationValue).toList();
    List<CoreAnnotationValue> metadataValues = applications.get(key);
    if (metadataValues != null && !metadataValues.equals(configured)) {
      throw new IllegalStateException("annotation metadata and interceptor disagree");
    }
    configurationSites.putIfAbsent(key, sourceSite);
    return execution.annotationExecution().value(this, key, configured, execution);
  }

  LifecycleDispatch functionBefore(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, functionInterceptor().before());
  }

  LifecycleDispatch functionAround(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, functionInterceptor().around());
  }

  LifecycleDispatch functionAfter(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, functionInterceptor().after());
  }

  LifecycleDispatch parameterBefore(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, parameterInterceptor().before());
  }

  LifecycleDispatch parameterAfter(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, parameterInterceptor().after());
  }

  LifecycleDispatch fieldBefore(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, fieldInterceptor().before());
  }

  LifecycleDispatch fieldAfter(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, fieldInterceptor().after());
  }

  private LifecycleDispatch dispatch(
      RuntimeValues.ObjectValue annotation, DefinitionId requirement) {
    RuntimeValues.DispatchTarget target =
        aggregateInfo(annotation.objectInfo.definition())
            .dispatch()
            .get(identity.slot(requirement));
    if (!(target instanceof RuntimeValues.DispatchTarget.Callable callable)) {
      throw new IllegalStateException("annotation lifecycle dispatch is absent");
    }
    List<CoreType> concreteArguments =
        annotation.type instanceof CoreType.Declared declared ? declared.arguments() : List.of();
    List<CoreType> receiverTypeArguments =
        callable.specializedReceiverTypeArguments()
            ? callable.receiverTypeArguments().stream()
                .map(type -> type.substitute(concreteArguments::get))
                .toList()
            : concreteArguments;
    return new LifecycleDispatch(callable.target(), receiverTypeArguments, version);
  }

  private AnnotationRetention retention(DefinitionId annotationId) {
    return program.annotationPolicy(annotationId).retention();
  }

  private CoreDefinition.Aggregate annotation(DefinitionId annotationId) {
    CoreDefinition definition = program.structure(annotationId).orElseThrow();
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalStateException("annotation definition is invalid");
    }
    return annotation;
  }

  private CoreFunctionInterceptorProtocol functionInterceptor() {
    if (functionInterceptor == null) {
      throw new IllegalStateException("std.annotation.FunctionInterceptor is absent");
    }
    return functionInterceptor;
  }

  private CoreParameterInterceptorProtocol parameterInterceptor() {
    if (parameterInterceptor == null) {
      throw new IllegalStateException("std.annotation.ParameterInterceptor is absent");
    }
    return parameterInterceptor;
  }

  private CoreFieldInterceptorProtocol fieldInterceptor() {
    if (fieldInterceptor == null) {
      throw new IllegalStateException("std.annotation.FieldInterceptor is absent");
    }
    return fieldInterceptor;
  }

  private ApplicationKey key(CoreAnnotationApplication application) {
    return switch (application.target()) {
      case CoreAnnotationTarget.Definition target ->
          new ApplicationKey(
              identity.id(application.annotation()),
              target.kind(),
              target.kind() == AnnotationTarget.FUNCTION
                      || target.kind() == AnnotationTarget.CONSTRUCTOR
                  ? identity.key(target.occurrence())
                  : identity.id(target.occurrence().representative()));
      case CoreAnnotationTarget.Parameter target ->
          new ApplicationKey(
              identity.id(application.annotation()),
              AnnotationTarget.PARAMETER,
              new IndexedKey(identity.key(target.callable()), target.index()));
      case CoreAnnotationTarget.Field target ->
          new ApplicationKey(
              identity.id(application.annotation()),
              AnnotationTarget.FIELD,
              fieldKey(target.owner(), target.ordinal()));
      default -> null;
    };
  }

  private RuntimeValues.FieldKey fieldKey(DefinitionOccurrenceId owner, int ordinal) {
    var aggregate =
        (CoreDefinition.Aggregate) program.structure(owner.representative()).orElseThrow();
    var field =
        aggregate.fields().stream()
            .filter(value -> value.ordinal() == ordinal)
            .findFirst()
            .orElseThrow();
    return new RuntimeValues.FieldKey(identity.id(owner.representative()), field.name());
  }

  private String displayName(CoreType type) {
    CoreType.Declared declared = declared(type);
    String name =
        switch (declared.constructor()) {
          case CoreTypeConstructor.Builtin builtin -> simpleName(builtin.id());
          case CoreTypeConstructor.User user -> nominal(resolveExternal(user.definition()));
        };
    if (!declared.arguments().isEmpty()) {
      name +=
          declared.arguments().stream()
              .map(this::displayName)
              .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
    }
    return declared.nullability() == CoreNullability.NULLABLE ? name + "?" : name;
  }

  private boolean matches(CoreType type, JarBindingClassReference reference) {
    try {
      return jarClassReference(type).equals(reference);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private CoreType reflectedType(JarBindingClassReference reference) {
    return switch (reference) {
      case JarBindingClassReference.Builtin builtin -> builtinType(builtin.typeId());
      case JarBindingClassReference.Nominal nominal -> {
        CoreDefinitionRecord record = nominalStructures.get(nominal);
        if (record == null)
          throw new IllegalArgumentException(
              "Java class maps to an unavailable Norm declaration: " + nominal);
        CoreDefinition definition = record.definition();
        yield new CoreType.Declared(
            new CoreTypeConstructor.User(new DefinitionReference.External(record.id())),
            java.util.Collections.nCopies(typeParameterCount(definition), CoreType.EXISTENTIAL),
            valueCategory(definition),
            CoreNullability.NON_NULL);
      }
    };
  }

  private static CoreType builtinType(String typeId) {
    return switch (typeId) {
      case "std.core.Any" -> CoreType.ANY;
      case "std.core.String" -> CoreType.STRING;
      case "std.core.Number" -> CoreType.NUMBER;
      case "std.core.Integer" -> CoreType.INTEGER;
      case "std.core.Long" -> CoreType.LONG;
      case "std.core.Float" -> CoreType.FLOAT;
      case "std.core.Double" -> CoreType.DOUBLE;
      case "std.core.Boolean" -> CoreType.BOOLEAN;
      default ->
          throw new IllegalArgumentException(
              "Java class maps to an unavailable Norm builtin: " + typeId);
    };
  }

  private static CoreNominalTypeKey nominalType(CoreDefinition definition) {
    return switch (definition) {
      case CoreDefinition.Aggregate aggregate -> aggregate.nominalType();
      case CoreDefinition.Enum declaration -> declaration.nominalType();
      case CoreDefinition.Interface declaration -> declaration.nominalType();
      default -> throw new IllegalArgumentException("Norm declaration is not a type");
    };
  }

  private static int typeParameterCount(CoreDefinition definition) {
    return switch (definition) {
      case CoreDefinition.Aggregate aggregate -> aggregate.typeParameters().size();
      case CoreDefinition.Enum declaration -> declaration.typeParameters().size();
      case CoreDefinition.Interface declaration -> declaration.typeParameters().size();
      default -> throw new IllegalArgumentException("Norm declaration is not a type");
    };
  }

  private static CoreValueCategory valueCategory(CoreDefinition definition) {
    return switch (definition) {
      case CoreDefinition.Aggregate aggregate -> aggregate.valueCategory();
      case CoreDefinition.Enum ignored -> CoreValueCategory.VALUE;
      case CoreDefinition.Interface ignored -> CoreValueCategory.POLYMORPHIC;
      default -> throw new IllegalArgumentException("Norm declaration is not a type");
    };
  }

  CoreType reflectedFieldType(RuntimeValues.FieldPlan field, CoreType.Declared reflected) {
    DefinitionId ownerId = field.owner().representative();
    CoreDefinition.Aggregate owner =
        (CoreDefinition.Aggregate) program.structure(ownerId).orElseThrow();
    CoreField declaration =
        owner.fields().stream()
            .filter(candidate -> candidate.ordinal() == field.index())
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("reflected field definition is absent"));
    CoreType absolute = CoreTypes.absolute(declaration.type(), ownerId, program);
    CoreType.Declared ownerView = aggregateView(reflected, ownerId);
    if (ownerView == null) {
      throw new IllegalArgumentException("reflected field owner is not an ancestor");
    }
    return absolute.substitute(
        index ->
            index < ownerView.arguments().size()
                ? ownerView.arguments().get(index)
                : new CoreType.Parameter(index, CoreNullability.NON_NULL));
  }

  private CoreType.Declared aggregateView(CoreType type, DefinitionId target) {
    return typeRelations.view(type, target);
  }

  private DefinitionId aggregateDefinition(CoreType.Declared type, String operation) {
    if (!(type.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException(operation + " require an aggregate type");
    }
    DefinitionId aggregateId = resolveExternal(user.definition());
    if (!(program.structure(aggregateId).orElseThrow() instanceof CoreDefinition.Aggregate)) {
      throw new IllegalArgumentException(operation + " require an aggregate type");
    }
    return aggregateId;
  }

  private DefinitionOccurrenceId occurrence(DefinitionId definition) {
    List<DefinitionOccurrenceId> occurrences =
        declarations.occurrences(identity.source(definition));
    if (occurrences.size() != 1) {
      throw new IllegalStateException("reflected declaration requires an exact occurrence");
    }
    return occurrences.getFirst();
  }

  private String nominal(DefinitionId id) {
    return switch (program.structure(id).orElseThrow()) {
      case CoreDefinition.Aggregate aggregate -> aggregate.nominalType().name();
      case CoreDefinition.Enum declaration -> declaration.nominalType().name();
      case CoreDefinition.Interface declaration -> declaration.nominalType().name();
      default -> throw new IllegalArgumentException("reflected type is not nominal");
    };
  }

  private DefinitionId resolve(DefinitionId owner, CoreDefinitionLink link) {
    return program.resolve(owner, (DefinitionReference) link);
  }

  private DefinitionId resolveExternal(CoreDefinitionLink link) {
    return identity.id(((DefinitionReference.External) link).definition());
  }

  private static CoreType.Declared declared(CoreType type) {
    if (!(type instanceof CoreType.Declared declared)) {
      throw new IllegalArgumentException("reflected type must be declared");
    }
    return declared;
  }

  private static String simpleName(BuiltinTypeId id) {
    int separator = id.value().lastIndexOf('.');
    return separator < 0 ? id.value() : id.value().substring(separator + 1);
  }

  private Object runtimeValue(CoreAnnotationValue value, CoreAnnotationReferenceSite site) {
    return switch (value.value()) {
      case CoreAnnotationValue.Null ignored -> RuntimeValues.NullValue.INSTANCE;
      case CoreAnnotationValue.Literal literal ->
          isCodePoint(value.type())
              ? new RuntimeValues.CodePointValue((Integer) literal.value())
              : literal.value();
      case CoreAnnotationValue.ListValue list -> {
        var values = new ArrayList<Object>();
        for (int index = 0; index < list.values().size(); index++)
          values.add(
              runtimeValue(
                  list.values().get(index), site.child(CoreAnnotationPath.Kind.LIST_ITEM, index)));
        yield new RuntimeValues.ListValue(value.type(), values);
      }
      case CoreAnnotationReference reference ->
          switch (reference) {
            case CoreAnnotationReference.ClassReference classReference ->
                new RuntimeValues.ClassValue(value.type(), classReference.reflectedType(), this);
            case CoreAnnotationReference.FieldReference fieldReference ->
                field(value.type(), fieldReference);
            case CoreAnnotationReference.EnumReference enumeration ->
                enumValue(value.type(), enumeration.variant());
            case CoreAnnotationReference.CallableReference callableReference -> {
              var occurrence = authoring.annotationTarget(site);
              DefinitionId callableId =
                  resolveExternal((DefinitionReference.External) callableReference.callable());
              CallTarget target = declarationTargets.get(occurrence);
              if (target == null) {
                throw new IllegalStateException("annotation callable reference is not initialized");
              }
              yield new RuntimeValues.Closure(
                  target,
                  occurrence,
                  callableReference.virtual() ? callableId : null,
                  program.callable(callableId).orElseThrow().hasReceiver(),
                  null,
                  new Object[0],
                  callableReference.receiverTypeArguments().toArray(),
                  callableReference.reifiedArguments().toArray(),
                  this);
            }
          };
    };
  }

  private RuntimeValues.EnumValue enumValue(CoreType type, String variant) {
    CoreType concrete =
        type instanceof CoreType.Declared declaredType
                && declaredType.nullability() != CoreNullability.NON_NULL
            ? new CoreType.Declared(
                declaredType.constructor(),
                declaredType.arguments(),
                declaredType.category(),
                CoreNullability.NON_NULL)
            : type;
    if (!(concrete instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalStateException("annotation enum value has an invalid type");
    }
    DefinitionId id = resolveExternal(user.definition());
    if (!(program.structure(id).orElseThrow() instanceof CoreDefinition.Enum enumeration)
        || enumeration.variants().stream().noneMatch(item -> item.key().equals(variant))) {
      throw new IllegalStateException("annotation enum variant is unavailable: " + variant);
    }
    return new RuntimeValues.EnumValue(
        id, concrete, enumeration.nominalType().name(), variant, List.of());
  }

  private static boolean isCodePoint(CoreType type) {
    return type instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.Builtin builtin
        && builtin.id().value().equals("std.core.CodePoint");
  }

  private RuntimeValues.ObjectValue createAnnotation(
      ApplicationKey key, List<CoreAnnotationValue> configured, ExecutionState execution) {
    DefinitionId annotationId = key.annotation();
    CoreDefinition.Aggregate declaration = annotation(annotationId);
    RuntimeValues.AggregateInfo info = aggregateInfo.get(annotationId);
    DefinitionId constructorId = resolve(annotationId, declaration.constructors().getFirst());
    CallTarget constructor = callableTargets.get(constructorId);
    if (info == null || constructor == null)
      throw new IllegalStateException("annotation runtime is not initialized");
    CoreType type =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new DefinitionReference.External(annotationId)),
            List.of(),
            CoreValueCategory.IDENTITY,
            CoreNullability.NON_NULL);
    var value = new RuntimeValues.ObjectValue(info, type);
    var site =
        java.util.Objects.requireNonNull(
            configurationSites.get(key), "annotation configuration authoring site");
    Object[] arguments = new Object[configured.size()];
    for (int index = 0; index < configured.size(); index++)
      arguments[index] =
          runtimeValue(configured.get(index), site.child(CoreAnnotationPath.Kind.VALUE, index));
    try (var scope =
        execution.liveBindings() == null || version == null
            ? null
            : execution.liveBindings().pin(version)) {
      constructor.call(
          execution,
          value,
          ConstructionArguments.supplied(
              configured.size(),
              java.util.stream.IntStream.range(0, configured.size()).toArray(),
              arguments,
              new Object[0]));
    }
    return value;
  }

  private Map<RuntimeValues.FieldKey, ConfiguredValue> configuration(
      ApplicationKey key, List<CoreAnnotationValue> configured) {
    DefinitionId annotationId = key.annotation();
    var declaration = annotation(annotationId);
    var constructor =
        program
            .callable(resolve(annotationId, declaration.constructors().getFirst()))
            .orElseThrow();
    var result = new LinkedHashMap<RuntimeValues.FieldKey, ConfiguredValue>();
    var root =
        java.util.Objects.requireNonNull(
            configurationSites.get(key), "annotation configuration authoring site");
    for (int index = 0; index < configured.size(); index++)
      result.put(
          new RuntimeValues.FieldKey(annotationId, constructor.parameters().get(index).name()),
          configuredValue(configured.get(index), root.child(CoreAnnotationPath.Kind.VALUE, index)));
    return Map.copyOf(result);
  }

  private ConfiguredValue configuredValue(
      CoreAnnotationValue value, CoreAnnotationReferenceSite site) {
    var references = new LinkedHashMap<CoreAnnotationPath, String>();
    authoring
        .annotationReferences()
        .forEach(
            (candidate, target) -> {
              if (candidate.owner().equals(site.owner())
                  && candidate.path().steps().size() >= site.path().steps().size()
                  && candidate
                      .path()
                      .steps()
                      .subList(0, site.path().steps().size())
                      .equals(site.path().steps()))
                references.put(
                    new CoreAnnotationPath(
                        candidate
                            .path()
                            .steps()
                            .subList(site.path().steps().size(), candidate.path().steps().size())),
                    identity.key(target));
            });
    return new ConfiguredValue(value, site, Map.copyOf(references));
  }

  private record ConfiguredValue(
      CoreAnnotationValue value,
      CoreAnnotationReferenceSite site,
      Map<CoreAnnotationPath, String> references) {
    @Override
    public boolean equals(Object other) {
      return other instanceof ConfiguredValue configured
          && value.equals(configured.value)
          && references.equals(configured.references);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(value, references);
    }
  }

  private static final class AnnotationState {
    private final RuntimeObjectState state;
    private final Map<RuntimeValues.AggregateInfo, Map<RuntimeValues.FieldKey, ConfiguredValue>>
        configurations = new java.util.WeakHashMap<>();

    private AnnotationState(
        RuntimeValues.ObjectValue initial,
        Map<RuntimeValues.FieldKey, ConfiguredValue> configured) {
      state = initial.state();
      configurations.put((RuntimeValues.AggregateInfo) initial.objectInfo, configured);
    }

    private RuntimeValues.ObjectValue view(
        AnnotationRuntime runtime, ApplicationKey key, List<CoreAnnotationValue> configured) {
      var retained = runtime.annotationViews.get(key);
      if (retained != null) return retained;
      var baseline = configurations.get(state.currentInfo());
      if (baseline == null)
        throw new IllegalStateException("Published annotation configuration is unavailable");
      var configuration = runtime.configuration(key, configured);
      var overlay = new LinkedHashMap<RuntimeValues.FieldKey, Object>();
      configuration.forEach(
          (field, value) -> {
            if (!value.equals(baseline.get(field)))
              overlay.put(field, runtime.runtimeValue(value.value(), value.site()));
          });
      var info = runtime.aggregateInfo(key.annotation());
      configurations.put(info, configuration);
      CoreType type =
          new CoreType.Declared(
              new CoreTypeConstructor.User(new DefinitionReference.External(key.annotation())),
              List.of(),
              CoreValueCategory.IDENTITY,
              CoreNullability.NON_NULL);
      var value = new RuntimeValues.ObjectValue(info, type, state.current(), overlay);
      runtime.annotationViews.put(key, value);
      return value;
    }
  }

  final class Execution {
    AnnotationRuntime runtime() {
      return AnnotationRuntime.this;
    }

    private final Map<ApplicationKey, AnnotationState> instances = new LinkedHashMap<>();

    private RuntimeValues.ObjectValue value(
        AnnotationRuntime runtime,
        ApplicationKey key,
        List<CoreAnnotationValue> configured,
        ExecutionState execution) {
      var current = instances.get(key);
      if (current != null) return current.view(runtime, key, configured);
      var value = runtime.createAnnotation(key, configured, execution);
      runtime.annotationViews.put(key, value);
      instances.put(key, new AnnotationState(value, runtime.configuration(key, configured)));
      return value;
    }
  }

  private record ApplicationKey(
      DefinitionId annotation, AnnotationTarget target, Object definition) {}

  private record TypeAnnotation(ApplicationKey key, List<CoreAnnotationValue> values) {
    private TypeAnnotation {
      values = List.copyOf(values);
    }
  }

  private record IndexedKey(String callable, int parameterIndex) {}

  record LifecycleDispatch(
      CallTarget target, List<CoreType> receiverTypeArguments, LiveBindings.Version version) {
    LifecycleDispatch {
      java.util.Objects.requireNonNull(target, "target");
      receiverTypeArguments = List.copyOf(receiverTypeArguments);
    }
  }
}
