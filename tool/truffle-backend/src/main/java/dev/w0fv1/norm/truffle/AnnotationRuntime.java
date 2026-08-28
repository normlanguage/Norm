package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreAggregateKind;
import dev.w0fv1.norm.core.CoreAnnotationApplication;
import dev.w0fv1.norm.core.CoreAnnotationPolicy;
import dev.w0fv1.norm.core.CoreAnnotationReference;
import dev.w0fv1.norm.core.CoreAnnotationTarget;
import dev.w0fv1.norm.core.CoreAnnotationValue;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreAuthoringMap;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreField;
import dev.w0fv1.norm.core.CoreFieldInterceptorProtocol;
import dev.w0fv1.norm.core.CoreFunctionInterceptorProtocol;
import dev.w0fv1.norm.core.CoreInterceptor;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreParameterInterceptorProtocol;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypes;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AnnotationRuntime {
  private final CoreProgram program;
  private final CoreAuthoringMap authoring;
  private final Map<ApplicationKey, List<CoreAnnotationValue>> applications;
  private final CoreFunctionInterceptorProtocol functionInterceptor;
  private final CoreParameterInterceptorProtocol parameterInterceptor;
  private final CoreFieldInterceptorProtocol fieldInterceptor;
  private final SerializationRuntime serialization;
  private final MapperEngine mapper;
  private final XmlDataFormat xml;
  private Map<DefinitionId, RuntimeValues.AggregateInfo> aggregateInfo = Map.of();
  private Map<DefinitionId, CallTarget> callableTargets = Map.of();

  AnnotationRuntime(CoreArtifact artifact) {
    program = artifact.program();
    authoring = artifact.authoring();
    Map<ApplicationKey, List<CoreAnnotationValue>> indexed = new LinkedHashMap<>();
    for (CoreAnnotationApplication application : artifact.metadata().annotations()) {
      ApplicationKey key = key(application);
      if (key != null) indexed.put(key, application.values());
    }
    applications = Map.copyOf(indexed);
    functionInterceptor = CoreFunctionInterceptorProtocol.resolve(program).orElse(null);
    parameterInterceptor = CoreParameterInterceptorProtocol.resolve(program).orElse(null);
    fieldInterceptor = CoreFieldInterceptorProtocol.resolve(program).orElse(null);
    serialization = new SerializationRuntime(this);
    mapper = new MapperEngine(serialization);
    xml = new XmlDataFormat(serialization);
  }

  void initialize(
      Map<DefinitionOccurrenceId, RuntimeValues.AggregateInfo> occurrences,
      Map<DefinitionId, CallTarget> callableTargets) {
    Map<DefinitionId, RuntimeValues.AggregateInfo> indexed = new LinkedHashMap<>();
    occurrences.forEach(
        (occurrence, info) -> indexed.putIfAbsent(occurrence.representative(), info));
    aggregateInfo = Map.copyOf(indexed);
    this.callableTargets = Map.copyOf(callableTargets);
  }

  String name(CoreType type) {
    return displayName(type);
  }

  RuntimeValues.ListValue fields(CoreType reflectedType, CoreType listType) {
    CoreType.Declared reflected = declared(reflectedType);
    if (!(reflected.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException("fields require an aggregate type");
    }
    DefinitionId aggregateId = resolveExternal(user.definition());
    if (!(program.definition(aggregateId).orElseThrow()
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
        (CoreDefinition.Aggregate) program.definition(aggregateId).orElseThrow();
    RuntimeValues.AggregateInfo info = aggregateInfo.get(aggregateId);
    if (info == null) throw new IllegalStateException("aggregate reflection is not initialized");
    List<Object> functions =
        aggregate.dispatch().stream()
            .map(dispatch -> resolve(aggregateId, dispatch.slot()))
            .distinct()
            .map(
                slot -> {
                  RuntimeValues.DispatchTarget target = info.dispatch().get(slot);
                  if (!(target instanceof RuntimeValues.DispatchTarget.Callable callable)) {
                    throw new IllegalStateException("reflected function target is absent");
                  }
                  return (Object)
                      new RuntimeValues.Closure(
                          callable.target(),
                          occurrence(slot),
                          slot,
                          true,
                          null,
                          new Object[0],
                          reflected.arguments().toArray(),
                          new Object[0]);
                })
            .toList();
    return new RuntimeValues.ListValue(listType, functions);
  }

  RuntimeValues.ListValue constructors(CoreType reflectedType, CoreType listType) {
    CoreType.Declared reflected = declared(reflectedType);
    DefinitionId aggregateId = aggregateDefinition(reflected, "constructors");
    CoreDefinition.Aggregate aggregate =
        (CoreDefinition.Aggregate) program.definition(aggregateId).orElseThrow();
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
    return authoring.origin(function.declaration()).definitionName();
  }

  Object functionOwner(RuntimeValues.Closure function, CoreType classType) {
    CoreDefinition definition =
        program.definition(function.declaration().representative()).orElseThrow();
    if (!(definition instanceof CoreDefinition.Callable callable)
        || callable.receiverType().isEmpty()) {
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
    CoreDefinition.Callable callable = callable(function);
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
    CoreDefinition.Callable callable = callable(function);
    if (index < 0 || index >= callable.parameters().size()) {
      throw new IllegalArgumentException("parameter declaration is absent");
    }
    dev.w0fv1.norm.core.CoreCallableParameter parameter = callable.parameters().get(index);
    CoreType valueType =
        CoreTypes.absolute(parameter.type(), function.declaration().representative(), program)
            .substitute(typeIndex -> callableTypeArgument(function, typeIndex));
    return new RuntimeValues.ParameterValue(
        parameterType, function, parameter.name(), valueType, this);
  }

  private CoreDefinition.Callable callable(RuntimeValues.Closure function) {
    CoreDefinition definition =
        program.definition(function.declaration().representative()).orElseThrow();
    if (!(definition instanceof CoreDefinition.Callable callable)) {
      throw new IllegalArgumentException("function declaration is not callable");
    }
    return callable;
  }

  private CoreType callableTypeArgument(RuntimeValues.Closure function, int index) {
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
            annotationId, AnnotationTarget.FIELD, new IndexedKey(field.owner(), field.index()));
    List<CoreAnnotationValue> values = applications.get(key);
    if (values == null || retention(annotationId) != AnnotationRetention.RUNTIME) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    return execution.annotationExecution().instance(key, values).value(execution);
  }

  Object readField(RuntimeValues.FieldValue field, Object receiver) {
    if (!(receiver instanceof RuntimeValues.ObjectValue object)
        || field.index() >= object.fields.length) {
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
    return RuntimeValues.copy(object.fields[field.index()]);
  }

  Execution execution() {
    return new Execution();
  }

  SerializationRuntime serialization() {
    return serialization;
  }

  MapperEngine mapper() {
    return mapper;
  }

  XmlDataFormat xml() {
    return xml;
  }

  CoreProgram program() {
    return program;
  }

  RuntimeValues.AggregateInfo aggregateInfo(DefinitionId definition) {
    return aggregateInfo.get(definition);
  }

  List<CoreAnnotationValue> typeAnnotationValues(DefinitionId annotation, DefinitionId type) {
    return applications.get(new ApplicationKey(annotation, AnnotationTarget.TYPE, type));
  }

  List<CoreAnnotationValue> fieldAnnotationValues(
      DefinitionId annotation, DefinitionOccurrenceId owner, int ordinal) {
    return applications.get(
        new ApplicationKey(annotation, AnnotationTarget.FIELD, new IndexedKey(owner, ordinal)));
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
    ApplicationKey key =
        new ApplicationKey(
            annotationId, AnnotationTarget.TYPE, resolveExternal(reflectedUser.definition()));
    List<CoreAnnotationValue> values = applications.get(key);
    if (values == null || retention(annotationId) != AnnotationRetention.RUNTIME) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    return execution.annotationExecution().instance(key, values).value(execution);
  }

  RuntimeValues.ObjectValue functionAnnotation(
      DefinitionOccurrenceId callable, CoreInterceptor interceptor, ExecutionState execution) {
    DefinitionId annotationId = resolve(callable.representative(), interceptor.annotation());
    return behaviorAnnotation(
        new ApplicationKey(annotationId, AnnotationTarget.FUNCTION, callable),
        interceptor,
        execution);
  }

  RuntimeValues.ObjectValue parameterAnnotation(
      DefinitionOccurrenceId callable,
      int parameterIndex,
      CoreInterceptor interceptor,
      ExecutionState execution) {
    DefinitionId annotationId = resolve(callable.representative(), interceptor.annotation());
    return behaviorAnnotation(
        new ApplicationKey(
            annotationId, AnnotationTarget.PARAMETER, new IndexedKey(callable, parameterIndex)),
        interceptor,
        execution);
  }

  RuntimeValues.ObjectValue fieldAnnotation(
      DefinitionOccurrenceId aggregate,
      int fieldIndex,
      CoreInterceptor interceptor,
      ExecutionState execution) {
    DefinitionId annotationId = resolve(aggregate.representative(), interceptor.annotation());
    return behaviorAnnotation(
        new ApplicationKey(
            annotationId, AnnotationTarget.FIELD, new IndexedKey(aggregate, fieldIndex)),
        interceptor,
        execution);
  }

  private RuntimeValues.ObjectValue behaviorAnnotation(
      ApplicationKey key, CoreInterceptor interceptor, ExecutionState execution) {
    List<CoreAnnotationValue> metadataValues = applications.get(key);
    if (metadataValues != null && !metadataValues.equals(interceptor.values())) {
      throw new IllegalStateException("annotation metadata and interceptor disagree");
    }
    return execution.annotationExecution().instance(key, interceptor.values()).value(execution);
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
    RuntimeValues.DispatchTarget target = annotation.objectInfo.dispatch().get(requirement);
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
    return new LifecycleDispatch(callable.target(), receiverTypeArguments);
  }

  private AnnotationRetention retention(DefinitionId annotationId) {
    CoreDefinition.Aggregate annotation = annotation(annotationId);
    return CoreAnnotationPolicy.resolve(program, annotationId, annotation).retention();
  }

  private CoreDefinition.Aggregate annotation(DefinitionId annotationId) {
    CoreDefinition definition = program.definition(annotationId).orElseThrow();
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
              application.annotation(),
              target.kind(),
              target.kind() == AnnotationTarget.FUNCTION
                  ? target.occurrence()
                  : target.occurrence().representative());
      case CoreAnnotationTarget.Parameter target ->
          new ApplicationKey(
              application.annotation(),
              AnnotationTarget.PARAMETER,
              new IndexedKey(target.callable(), target.index()));
      case CoreAnnotationTarget.Field target ->
          new ApplicationKey(
              application.annotation(),
              AnnotationTarget.FIELD,
              new IndexedKey(target.owner(), target.ordinal()));
      default -> null;
    };
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

  CoreType reflectedFieldType(RuntimeValues.FieldPlan field, CoreType.Declared reflected) {
    DefinitionId ownerId = field.owner().representative();
    CoreDefinition.Aggregate owner =
        (CoreDefinition.Aggregate) program.definition(ownerId).orElseThrow();
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
    CoreType current = type;
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
          CoreTypes.absolute(aggregate.parentType().orElseThrow(), id, program)
              .substitute(declared.arguments()::get);
    }
    return null;
  }

  private DefinitionId aggregateDefinition(CoreType.Declared type, String operation) {
    if (!(type.constructor() instanceof CoreTypeConstructor.User user)) {
      throw new IllegalArgumentException(operation + " require an aggregate type");
    }
    DefinitionId aggregateId = resolveExternal(user.definition());
    if (!(program.definition(aggregateId).orElseThrow() instanceof CoreDefinition.Aggregate)) {
      throw new IllegalArgumentException(operation + " require an aggregate type");
    }
    return aggregateId;
  }

  private DefinitionOccurrenceId occurrence(DefinitionId definition) {
    List<dev.w0fv1.norm.core.CoreDefinitionOccurrence> occurrences =
        authoring.occurrences(definition);
    if (occurrences.isEmpty()) {
      throw new IllegalStateException("reflected declaration occurrence is absent");
    }
    return occurrences.getFirst().id();
  }

  private String nominal(DefinitionId id) {
    return switch (program.definition(id).orElseThrow()) {
      case CoreDefinition.Aggregate aggregate -> aggregate.nominalType().name();
      case CoreDefinition.Enum declaration -> declaration.nominalType().name();
      case CoreDefinition.Interface declaration -> declaration.nominalType().name();
      default -> throw new IllegalArgumentException("reflected type is not nominal");
    };
  }

  private DefinitionId resolve(DefinitionId owner, dev.w0fv1.norm.core.CoreDefinitionLink link) {
    return program.resolve(owner, (DefinitionReference) link);
  }

  private static DefinitionId resolveExternal(dev.w0fv1.norm.core.CoreDefinitionLink link) {
    return ((DefinitionReference.External) link).definition();
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

  private Object runtimeValue(CoreAnnotationValue value) {
    return switch (value.value()) {
      case CoreAnnotationValue.Null ignored -> RuntimeValues.NullValue.INSTANCE;
      case CoreAnnotationValue.Literal literal ->
          isCodePoint(value.type())
              ? new RuntimeValues.CodePointValue((Integer) literal.value())
              : literal.value();
      case CoreAnnotationValue.ListValue list ->
          new RuntimeValues.ListValue(
              value.type(), list.values().stream().map(this::runtimeValue).toList());
      case CoreAnnotationReference reference ->
          switch (reference) {
            case CoreAnnotationReference.ClassReference classReference ->
                new RuntimeValues.ClassValue(value.type(), classReference.reflectedType(), this);
            case CoreAnnotationReference.FieldReference fieldReference ->
                field(value.type(), fieldReference);
            case CoreAnnotationReference.CallableReference callableReference -> {
              DefinitionId callableId =
                  resolveExternal((DefinitionReference.External) callableReference.callable());
              CallTarget target = callableTargets.get(callableId);
              if (target == null) {
                throw new IllegalStateException("annotation callable reference is not initialized");
              }
              yield new RuntimeValues.Closure(
                  target,
                  occurrence(callableId),
                  callableReference.virtual() ? callableId : null,
                  ((CoreDefinition.Callable) program.definition(callableId).orElseThrow())
                      .hasReceiver(),
                  null,
                  new Object[0],
                  callableReference.receiverTypeArguments().toArray(),
                  callableReference.reifiedArguments().toArray());
            }
          };
    };
  }

  private static boolean isCodePoint(CoreType type) {
    return type instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.Builtin builtin
        && builtin.id().value().equals("std.core.CodePoint");
  }

  private final class AnnotationInstance {
    private final DefinitionId annotationId;
    private final List<CoreAnnotationValue> values;
    private RuntimeValues.ObjectValue value;

    private AnnotationInstance(DefinitionId annotationId, List<CoreAnnotationValue> values) {
      this.annotationId = annotationId;
      this.values = List.copyOf(values);
    }

    private RuntimeValues.ObjectValue value(ExecutionState execution) {
      if (value != null) return value;
      CoreDefinition.Aggregate declaration = annotation(annotationId);
      RuntimeValues.AggregateInfo info = aggregateInfo.get(annotationId);
      DefinitionId constructorId = resolve(annotationId, declaration.constructors().getFirst());
      CallTarget constructor = callableTargets.get(constructorId);
      if (info == null || constructor == null) {
        throw new IllegalStateException("annotation runtime is not initialized");
      }
      CoreType type =
          new CoreType.Declared(
              new CoreTypeConstructor.User(new DefinitionReference.External(annotationId)),
              List.of(),
              CoreValueCategory.IDENTITY,
              CoreNullability.NON_NULL);
      value = new RuntimeValues.ObjectValue(info, type);
      Object[] arguments = new Object[values.size() + 2];
      arguments[0] = execution;
      arguments[1] = value;
      for (int index = 0; index < values.size(); index++) {
        arguments[index + 2] = runtimeValue(values.get(index));
      }
      try {
        constructor.call(arguments);
        return value;
      } catch (RuntimeException | Error failure) {
        value = null;
        throw failure;
      }
    }
  }

  final class Execution {
    private final Map<ApplicationKey, AnnotationInstance> instances = new LinkedHashMap<>();

    private AnnotationInstance instance(ApplicationKey key, List<CoreAnnotationValue> values) {
      AnnotationInstance current = instances.get(key);
      if (current != null) return current;
      AnnotationInstance created = new AnnotationInstance(key.annotation(), values);
      instances.put(key, created);
      return created;
    }
  }

  private record ApplicationKey(
      DefinitionId annotation, AnnotationTarget target, Object definition) {}

  private record IndexedKey(DefinitionOccurrenceId callable, int parameterIndex) {}

  record LifecycleDispatch(CallTarget target, List<CoreType> receiverTypeArguments) {
    LifecycleDispatch {
      java.util.Objects.requireNonNull(target, "target");
      receiverTypeArguments = List.copyOf(receiverTypeArguments);
    }
  }
}
