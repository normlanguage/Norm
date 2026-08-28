package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CoreAnnotationVerifier {
  private CoreAnnotationVerifier() {}

  static void verifyArtifact(
      CoreProgram program, CoreAuthoringMap authoring, CoreMetadata metadata) {
    CoreFunctionInterceptorProtocol functionInterceptor =
        CoreFunctionInterceptorProtocol.resolve(program).orElse(null);
    CoreParameterInterceptorProtocol parameterInterceptor =
        CoreParameterInterceptorProtocol.resolve(program).orElse(null);
    CoreFieldInterceptorProtocol fieldInterceptor =
        CoreFieldInterceptorProtocol.resolve(program).orElse(null);
    Map<ApplicationKey, CoreAnnotationApplication> applications = new LinkedHashMap<>();
    for (CoreAnnotationApplication application : metadata.annotations()) {
      verifyApplication(program, authoring, application);
      if (applications.putIfAbsent(applicationKey(application), application) != null) {
        throw new IllegalArgumentException("annotation application must be unique per target");
      }
    }
    Set<ApplicationKey> matched = new HashSet<>();
    for (CoreDefinitionOccurrence occurrence : authoring.occurrences()) {
      CoreDefinition definition =
          program.definition(occurrence.id().representative()).orElseThrow();
      if (definition instanceof CoreDefinition.Aggregate aggregate
          && occurrence.role() == CoreDefinitionRole.AGGREGATE) {
        for (CoreField field : aggregate.fields()) {
          for (CoreInterceptor interceptor : field.interceptors()) {
            matchInterceptor(
                program,
                occurrence.id(),
                AnnotationTarget.FIELD,
                new IndexedKey(occurrence.id(), field.ordinal()),
                interceptor,
                applications,
                matched);
          }
        }
      }
      if (!(definition instanceof CoreDefinition.Callable callable)) continue;
      for (CoreInterceptor interceptor : callable.interceptors()) {
        matchInterceptor(
            program,
            occurrence.id(),
            AnnotationTarget.FUNCTION,
            new DefinitionKey(AnnotationTarget.FUNCTION, occurrence.id()),
            interceptor,
            applications,
            matched);
      }
      for (int index = 0; index < callable.parameters().size(); index++) {
        for (CoreInterceptor interceptor : callable.parameters().get(index).interceptors()) {
          matchInterceptor(
              program,
              occurrence.id(),
              AnnotationTarget.PARAMETER,
              new IndexedKey(occurrence.id(), index),
              interceptor,
              applications,
              matched);
        }
      }
    }
    for (Map.Entry<ApplicationKey, CoreAnnotationApplication> entry : applications.entrySet()) {
      ApplicationKey application = entry.getKey();
      if (isBehaviorApplication(
              program,
              entry.getValue(),
              functionInterceptor,
              parameterInterceptor,
              fieldInterceptor)
          && !matched.contains(application)) {
        throw new IllegalArgumentException(
            "stored behavior annotation requires a declaration interceptor");
      }
    }
  }

  private static void matchInterceptor(
      CoreProgram program,
      DefinitionOccurrenceId owner,
      AnnotationTarget target,
      Object targetKey,
      CoreInterceptor interceptor,
      Map<ApplicationKey, CoreAnnotationApplication> applications,
      Set<ApplicationKey> matched) {
    DefinitionId annotationId = resolve(program, owner.representative(), interceptor.annotation());
    CoreDefinition.Aggregate annotation =
        (CoreDefinition.Aggregate) program.definition(annotationId).orElseThrow();
    AnnotationRetention retention =
        CoreAnnotationPolicy.resolve(program, annotationId, annotation).retention();
    ApplicationKey key = new ApplicationKey(annotationId, target, targetKey);
    CoreAnnotationApplication application = applications.get(key);
    if (retention == AnnotationRetention.SOURCE) {
      if (application != null) {
        throw new IllegalArgumentException("source interceptor cannot be stored in metadata");
      }
      return;
    }
    if (application == null || !application.values().equals(interceptor.values())) {
      throw new IllegalArgumentException(
          "stored behavior annotation must match its declaration interceptor");
    }
    matched.add(key);
  }

  static void verifyInterceptor(
      CoreProgram program,
      DefinitionId callableId,
      CoreInterceptor interceptor,
      CoreFunctionInterceptorProtocol functionInterceptor) {
    CoreDefinition.Callable callable =
        (CoreDefinition.Callable) program.definition(callableId).orElseThrow();
    if (CoreTypes.containsReference(callable.returnType())
        || callable.parameterTypes().stream().anyMatch(CoreTypes::containsReference)) {
      throw new IllegalArgumentException("interceptor callable cannot use reference types");
    }
    DefinitionId annotationId = resolve(program, callableId, interceptor.annotation());
    CoreDefinition definition =
        program
            .definition(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("interceptor annotation is absent"));
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("interceptor must reference an annotation");
    }
    CoreAnnotationPolicy policy = CoreAnnotationPolicy.resolve(program, annotationId, annotation);
    if (!policy.targets().contains(dev.w0fv1.norm.value.AnnotationTarget.FUNCTION)) {
      throw new IllegalArgumentException("interceptor annotation must allow function targets");
    }
    if (functionInterceptor == null
        || !implementsProtocol(
            program, annotationId, annotation, functionInterceptor.interfaceId())) {
      throw new IllegalArgumentException(
          "interceptor annotation must implement FunctionInterceptor");
    }
    verifyValues(program, callableId, annotationId, annotation, interceptor.values());
  }

  static void verifyParameterInterceptor(
      CoreProgram program,
      DefinitionId callableId,
      CoreCallableParameter parameter,
      CoreInterceptor interceptor,
      CoreParameterInterceptorProtocol parameterInterceptor) {
    if (CoreTypes.containsReference(parameter.type())) {
      throw new IllegalArgumentException("interceptor parameter cannot use a reference type");
    }
    DefinitionId annotationId = resolve(program, callableId, interceptor.annotation());
    CoreDefinition definition =
        program
            .definition(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("interceptor annotation is absent"));
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("interceptor must reference an annotation");
    }
    CoreAnnotationPolicy policy = CoreAnnotationPolicy.resolve(program, annotationId, annotation);
    if (!policy.targets().contains(AnnotationTarget.PARAMETER)) {
      throw new IllegalArgumentException("interceptor annotation must allow parameter targets");
    }
    if (parameterInterceptor == null) {
      throw new IllegalArgumentException("ParameterInterceptor protocol is absent");
    }
    CoreType expected =
        targetType(
            program,
            annotationId,
            annotation,
            parameterInterceptor.interfaceId(),
            "ParameterInterceptor");
    CoreType actual = CoreTypes.absolute(parameter.type(), callableId, program);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("ParameterInterceptor type does not match parameter type");
    }
    verifyValues(program, callableId, annotationId, annotation, interceptor.values());
  }

  static void verifyFieldInterceptor(
      CoreProgram program,
      DefinitionId aggregateId,
      CoreField field,
      CoreInterceptor interceptor,
      CoreFieldInterceptorProtocol fieldInterceptor) {
    DefinitionId annotationId = resolve(program, aggregateId, interceptor.annotation());
    CoreDefinition definition =
        program
            .definition(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("interceptor annotation is absent"));
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("interceptor must reference an annotation");
    }
    CoreAnnotationPolicy policy = CoreAnnotationPolicy.resolve(program, annotationId, annotation);
    if (!policy.targets().contains(AnnotationTarget.FIELD)) {
      throw new IllegalArgumentException("interceptor annotation must allow field targets");
    }
    if (fieldInterceptor == null) {
      throw new IllegalArgumentException("FieldInterceptor protocol is absent");
    }
    CoreType expected =
        targetType(
            program, annotationId, annotation, fieldInterceptor.interfaceId(), "FieldInterceptor");
    CoreType actual = CoreTypes.absolute(field.type(), aggregateId, program);
    if (!actual.equals(expected)) {
      throw new IllegalArgumentException("FieldInterceptor type does not match field type");
    }
    verifyValues(program, aggregateId, annotationId, annotation, interceptor.values());
  }

  private static CoreType targetType(
      CoreProgram program,
      DefinitionId annotationId,
      CoreDefinition.Aggregate annotation,
      DefinitionId protocol,
      String name) {
    Map<DefinitionId, CoreType.Declared> interfaces = new LinkedHashMap<>();
    CoreInterfaceHierarchy hierarchy = new CoreInterfaceHierarchy(program);
    for (CoreConformance conformance : annotation.conformances()) {
      hierarchy.collect(annotationId, conformance.interfaceType(), interfaces);
    }
    CoreType.Declared target = interfaces.get(protocol);
    if (target == null || target.arguments().size() != 1) {
      throw new IllegalArgumentException("interceptor annotation must implement " + name);
    }
    return target.arguments().getFirst();
  }

  private static boolean isBehaviorApplication(
      CoreProgram program,
      CoreAnnotationApplication application,
      CoreFunctionInterceptorProtocol functionInterceptor,
      CoreParameterInterceptorProtocol parameterInterceptor,
      CoreFieldInterceptorProtocol fieldInterceptor) {
    DefinitionId annotationId = application.annotation();
    CoreDefinition.Aggregate annotation =
        (CoreDefinition.Aggregate) program.definition(annotationId).orElseThrow();
    DefinitionId protocol =
        switch (application.target().kind()) {
          case FUNCTION -> functionInterceptor == null ? null : functionInterceptor.interfaceId();
          case PARAMETER ->
              parameterInterceptor == null ? null : parameterInterceptor.interfaceId();
          case FIELD -> fieldInterceptor == null ? null : fieldInterceptor.interfaceId();
          default -> null;
        };
    return protocol != null && implementsProtocol(program, annotationId, annotation, protocol);
  }

  private static boolean implementsProtocol(
      CoreProgram program,
      DefinitionId annotationId,
      CoreDefinition.Aggregate annotation,
      DefinitionId protocol) {
    Map<DefinitionId, CoreType.Declared> interfaces = new LinkedHashMap<>();
    CoreInterfaceHierarchy hierarchy = new CoreInterfaceHierarchy(program);
    for (CoreConformance conformance : annotation.conformances()) {
      hierarchy.collect(annotationId, conformance.interfaceType(), interfaces);
    }
    return interfaces.containsKey(protocol);
  }

  static void verifyDeclaration(
      CoreProgram program, DefinitionId annotationId, CoreDefinition.Aggregate annotation) {
    DefinitionId constructorId = resolve(program, annotationId, annotation.constructor());
    CoreDefinition.Callable constructor = callable(program, constructorId);
    constructor.parameterTypes().forEach(type -> requireMetadataType(program, constructorId, type));
  }

  private static void verifyApplication(
      CoreProgram program, CoreAuthoringMap authoring, CoreAnnotationApplication application) {
    DefinitionId annotationId = application.annotation();
    CoreDefinition definition =
        program
            .definition(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("annotation definition is absent"));
    if (!(definition instanceof CoreDefinition.Aggregate annotation)
        || annotation.kind() != CoreAggregateKind.ANNOTATION) {
      throw new IllegalArgumentException("annotation application must reference an annotation");
    }
    CoreAnnotationPolicy policy = CoreAnnotationPolicy.resolve(program, annotationId, annotation);
    if (policy.retention() == AnnotationRetention.SOURCE) {
      throw new IllegalArgumentException("source annotation cannot be stored in Core");
    }
    if (!policy.targets().contains(application.target().kind())) {
      throw new IllegalArgumentException("annotation application target is not allowed");
    }
    verifyTarget(program, authoring, application.target());
    verifyValues(program, annotationId, annotationId, annotation, application.values());
  }

  private static void verifyValues(
      CoreProgram program,
      DefinitionId valueOwner,
      DefinitionId annotationId,
      CoreDefinition.Aggregate annotation,
      List<CoreAnnotationValue> values) {
    DefinitionId constructorId = resolve(program, annotationId, annotation.constructor());
    CoreDefinition constructorDefinition = program.definition(constructorId).orElseThrow();
    if (!(constructorDefinition instanceof CoreDefinition.Callable constructor)) {
      throw new IllegalArgumentException("annotation constructor is not callable");
    }
    if (values.size() != constructor.parameterTypes().size()) {
      throw new IllegalArgumentException("annotation application values are not normalized");
    }
    for (int index = 0; index < values.size(); index++) {
      verifyValue(
          program,
          valueOwner,
          constructorId,
          constructor.parameterTypes().get(index),
          values.get(index));
    }
  }

  private static ApplicationKey applicationKey(CoreAnnotationApplication application) {
    Object target =
        switch (application.target()) {
          case CoreAnnotationTarget.Package targetPackage ->
              new PackageKey(targetPackage.module(), targetPackage.packageName());
          case CoreAnnotationTarget.Definition definition ->
              new DefinitionKey(definition.kind(), definition.occurrence());
          case CoreAnnotationTarget.Field field -> new IndexedKey(field.owner(), field.ordinal());
          case CoreAnnotationTarget.Parameter parameter ->
              new IndexedKey(parameter.callable(), parameter.index());
          case CoreAnnotationTarget.Local local -> new IndexedKey(local.callable(), local.index());
        };
    return new ApplicationKey(application.annotation(), application.target().kind(), target);
  }

  private static void verifyTarget(
      CoreProgram program, CoreAuthoringMap authoring, CoreAnnotationTarget target) {
    switch (target) {
      case CoreAnnotationTarget.Package targetPackage -> {
        if (targetPackage.packageName().isBlank()) {
          throw new IllegalArgumentException("annotation package target must not be blank");
        }
      }
      case CoreAnnotationTarget.Definition definition -> {
        CoreDefinitionOccurrence occurrence = requireOccurrence(authoring, definition.occurrence());
        CoreDefinition targetDefinition =
            definition(program, definition.occurrence().representative());
        boolean valid =
            switch (definition.kind()) {
              case TYPE ->
                  occurrence.role() == CoreDefinitionRole.AGGREGATE
                          && targetDefinition instanceof CoreDefinition.Aggregate
                      || occurrence.role() == CoreDefinitionRole.ENUM
                          && targetDefinition instanceof CoreDefinition.Enum
                      || occurrence.role() == CoreDefinitionRole.INTERFACE
                          && targetDefinition instanceof CoreDefinition.Interface;
              case CONSTRUCTOR ->
                  occurrence.role() == CoreDefinitionRole.CONSTRUCTOR
                      && targetDefinition instanceof CoreDefinition.Callable;
              case FUNCTION ->
                  (occurrence.role() == CoreDefinitionRole.FUNCTION
                          || occurrence.role() == CoreDefinitionRole.EXTENSION
                          || occurrence.role() == CoreDefinitionRole.METHOD)
                      && targetDefinition instanceof CoreDefinition.Callable;
              default -> false;
            };
        if (!valid) {
          throw new IllegalArgumentException(
              "annotation "
                  + definition.kind().keyword()
                  + " target has the wrong Core kind: "
                  + definition.occurrence());
        }
      }
      case CoreAnnotationTarget.Field field -> {
        CoreDefinitionOccurrence occurrence = requireOccurrence(authoring, field.owner());
        CoreDefinition targetDefinition = definition(program, field.owner().representative());
        if (occurrence.role() != CoreDefinitionRole.AGGREGATE) {
          throw new IllegalArgumentException("annotation field target has the wrong role");
        }
        if (!(targetDefinition instanceof CoreDefinition.Aggregate aggregate)) {
          throw new IllegalArgumentException("annotation field target has no fields");
        }
        List<CoreField> fields = aggregate.fields();
        if (fields.stream().noneMatch(candidate -> candidate.ordinal() == field.ordinal())) {
          throw new IllegalArgumentException("annotation field target is outside its owner");
        }
      }
      case CoreAnnotationTarget.Parameter parameter -> {
        CoreDefinitionOccurrence occurrence = requireOccurrence(authoring, parameter.callable());
        if (occurrence.role() != CoreDefinitionRole.CONSTRUCTOR
            && occurrence.role() != CoreDefinitionRole.FUNCTION
            && occurrence.role() != CoreDefinitionRole.EXTENSION
            && occurrence.role() != CoreDefinitionRole.METHOD
            && occurrence.role() != CoreDefinitionRole.INTERFACE_METHOD) {
          throw new IllegalArgumentException("annotation parameter target has the wrong role");
        }
        CoreDefinition targetDefinition =
            definition(program, parameter.callable().representative());
        int parameterCount =
            switch (targetDefinition) {
              case CoreDefinition.Callable callable -> callable.parameterTypes().size();
              case CoreDefinition.InterfaceMethod method -> method.parameterTypes().size();
              default ->
                  throw new IllegalArgumentException("annotation parameter target is not callable");
            };
        if (parameter.index() >= parameterCount) {
          throw new IllegalArgumentException("annotation parameter target is outside its callable");
        }
      }
      case CoreAnnotationTarget.Local local -> {
        CoreDefinitionOccurrence occurrence = requireOccurrence(authoring, local.callable());
        if (occurrence.role() != CoreDefinitionRole.CONSTRUCTOR
            && occurrence.role() != CoreDefinitionRole.FUNCTION
            && occurrence.role() != CoreDefinitionRole.EXTENSION
            && occurrence.role() != CoreDefinitionRole.METHOD
            && occurrence.role() != CoreDefinitionRole.LAMBDA) {
          throw new IllegalArgumentException("annotation local target has the wrong role");
        }
        CoreDefinition.Callable callable = callable(program, local.callable().representative());
        if (local.index() >= callable.locals().size()
            || callable.locals().get(local.index()).kind() != CoreLocal.Kind.VARIABLE) {
          throw new IllegalArgumentException("annotation local target is outside its callable");
        }
      }
    }
  }

  private static void verifyValue(
      CoreProgram program,
      DefinitionId valueOwner,
      DefinitionId expectedOwner,
      CoreType expected,
      CoreAnnotationValue value) {
    CoreType absoluteExpected = CoreTypes.absolute(expected, expectedOwner, program);
    CoreType absoluteActual = CoreTypes.absolute(value.type(), valueOwner, program);
    if (!absoluteExpected.equals(absoluteActual)) {
      throw new IllegalArgumentException(
          "annotation value type does not match its constructor parameter");
    }
    requireMetadataType(program, valueOwner, value.type());
    Object raw = value.value();
    if (raw == null) {
      if (!value.type().isNullable()) {
        throw new IllegalArgumentException(
            "null annotation value requires a nullable constructor parameter");
      }
      return;
    }
    CoreType nonNull = nonNullable(absoluteActual);
    boolean valid =
        nonNull.equals(CoreType.BOOLEAN) && raw instanceof Boolean
            || nonNull.equals(CoreType.CODE_POINT) && raw instanceof Integer
            || nonNull.equals(CoreType.INTEGER) && raw instanceof Integer
            || nonNull.equals(CoreType.LONG) && raw instanceof Long
            || nonNull.equals(CoreType.FLOAT) && raw instanceof Float
            || nonNull.equals(CoreType.DOUBLE) && raw instanceof Double
            || nonNull.equals(CoreType.STRING) && raw instanceof String;
    if (!valid)
      throw new IllegalArgumentException("annotation value has an invalid representation");
  }

  private static void requireMetadataType(CoreProgram program, DefinitionId owner, CoreType type) {
    CoreType absolute = CoreTypes.absolute(type, owner, program);
    CoreType nonNull = nonNullable(absolute);
    if (!nonNull.equals(CoreType.BOOLEAN)
        && !nonNull.equals(CoreType.CODE_POINT)
        && !nonNull.equals(CoreType.INTEGER)
        && !nonNull.equals(CoreType.LONG)
        && !nonNull.equals(CoreType.FLOAT)
        && !nonNull.equals(CoreType.DOUBLE)
        && !nonNull.equals(CoreType.STRING)) {
      throw new IllegalArgumentException(
          "annotation constructor parameter is not a metadata scalar");
    }
  }

  private static CoreType nonNullable(CoreType type) {
    if (!(type instanceof CoreType.Declared declared)
        || declared.nullability() == CoreNullability.NON_NULL) {
      return type;
    }
    return new CoreType.Declared(
        declared.constructor(),
        declared.arguments(),
        declared.category(),
        CoreNullability.NON_NULL);
  }

  private static CoreDefinition definition(CoreProgram program, DefinitionId id) {
    return program
        .definition(id)
        .orElseThrow(() -> new IllegalArgumentException("annotation target is absent"));
  }

  private static CoreDefinition.Callable callable(CoreProgram program, DefinitionId id) {
    if (!(definition(program, id) instanceof CoreDefinition.Callable callable)) {
      throw new IllegalArgumentException("annotation target must be callable");
    }
    return callable;
  }

  private static CoreDefinitionOccurrence requireOccurrence(
      CoreAuthoringMap authoring, DefinitionOccurrenceId occurrence) {
    return authoring
        .occurrence(occurrence)
        .orElseThrow(() -> new IllegalArgumentException("annotation target occurrence is absent"));
  }

  private static DefinitionId resolve(
      CoreProgram program, DefinitionId owner, CoreDefinitionLink link) {
    if (!(link instanceof DefinitionReference reference)) {
      throw new IllegalArgumentException("annotation contains a pending reference");
    }
    return program.resolve(owner, reference);
  }

  record ApplicationKey(
      DefinitionId annotation, dev.w0fv1.norm.value.AnnotationTarget kind, Object target) {}

  private record PackageKey(dev.w0fv1.norm.value.ModuleCoordinate module, String packageName) {}

  private record DefinitionKey(
      dev.w0fv1.norm.value.AnnotationTarget kind, DefinitionOccurrenceId definition) {}

  private record IndexedKey(DefinitionOccurrenceId owner, int index) {}
}
