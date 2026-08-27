package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreAggregateKind;
import dev.w0fv1.norm.core.CoreAnnotationApplication;
import dev.w0fv1.norm.core.CoreAnnotationPolicy;
import dev.w0fv1.norm.core.CoreAnnotationTarget;
import dev.w0fv1.norm.core.CoreAnnotationValue;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreFieldTargetProtocol;
import dev.w0fv1.norm.core.CoreFunctionTargetProtocol;
import dev.w0fv1.norm.core.CoreInterceptor;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreParameterTargetProtocol;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AnnotationRuntime {
  private final CoreProgram program;
  private final Map<ApplicationKey, List<CoreAnnotationValue>> applications;
  private final CoreFunctionTargetProtocol functionTarget;
  private final CoreParameterTargetProtocol parameterTarget;
  private final CoreFieldTargetProtocol fieldTarget;
  private Map<DefinitionId, RuntimeValues.AggregateInfo> aggregateInfo = Map.of();
  private Map<DefinitionId, CallTarget> constructors = Map.of();

  AnnotationRuntime(CoreArtifact artifact) {
    program = artifact.program();
    Map<ApplicationKey, List<CoreAnnotationValue>> indexed = new LinkedHashMap<>();
    for (CoreAnnotationApplication application : artifact.metadata().annotations()) {
      ApplicationKey key = key(application);
      if (key != null) indexed.put(key, application.values());
    }
    applications = Map.copyOf(indexed);
    functionTarget = CoreFunctionTargetProtocol.resolve(program).orElse(null);
    parameterTarget = CoreParameterTargetProtocol.resolve(program).orElse(null);
    fieldTarget = CoreFieldTargetProtocol.resolve(program).orElse(null);
  }

  void initialize(
      Map<DefinitionOccurrenceId, RuntimeValues.AggregateInfo> occurrences,
      Map<DefinitionId, CallTarget> callableTargets) {
    Map<DefinitionId, RuntimeValues.AggregateInfo> indexed = new LinkedHashMap<>();
    occurrences.forEach(
        (occurrence, info) -> indexed.putIfAbsent(occurrence.representative(), info));
    aggregateInfo = Map.copyOf(indexed);
    constructors = Map.copyOf(callableTargets);
  }

  String name(CoreType type) {
    return displayName(type);
  }

  Execution execution() {
    return new Execution();
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
    return dispatch(annotation, functionTarget().before());
  }

  LifecycleDispatch functionAround(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, functionTarget().around());
  }

  LifecycleDispatch functionAfter(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, functionTarget().after());
  }

  LifecycleDispatch parameterBefore(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, parameterTarget().before());
  }

  LifecycleDispatch parameterAfter(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, parameterTarget().after());
  }

  LifecycleDispatch fieldBefore(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, fieldTarget().before());
  }

  LifecycleDispatch fieldAfter(RuntimeValues.ObjectValue annotation) {
    return dispatch(annotation, fieldTarget().after());
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

  private CoreFunctionTargetProtocol functionTarget() {
    if (functionTarget == null) {
      throw new IllegalStateException("std.annotation.FunctionTarget is absent");
    }
    return functionTarget;
  }

  private CoreParameterTargetProtocol parameterTarget() {
    if (parameterTarget == null) {
      throw new IllegalStateException("std.annotation.ParameterTarget is absent");
    }
    return parameterTarget;
  }

  private CoreFieldTargetProtocol fieldTarget() {
    if (fieldTarget == null) {
      throw new IllegalStateException("std.annotation.FieldTarget is absent");
    }
    return fieldTarget;
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

  private static Object runtimeValue(CoreAnnotationValue value) {
    if (value.value() == null) return RuntimeValues.NullValue.INSTANCE;
    return isCodePoint(value.type())
        ? new RuntimeValues.CodePointValue((Integer) value.value())
        : value.value();
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
      DefinitionId constructorId = resolve(annotationId, declaration.constructor());
      CallTarget constructor = constructors.get(constructorId);
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
