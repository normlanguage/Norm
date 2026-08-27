package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationRetention;
import java.util.List;

final class CoreAnnotationVerifier {
  private CoreAnnotationVerifier() {}

  static void verifySchema(
      CoreProgram program, DefinitionId owner, CoreDefinition.Annotation annotation) {
    for (CoreField field : annotation.fields()) {
      requireMetadataType(program, owner, field.type());
    }
    for (int index = 0; index < annotation.defaults().size(); index++) {
      int fieldIndex = index;
      annotation
          .defaults()
          .get(index)
          .ifPresent(
              value ->
                  verifyValue(program, owner, annotation.fields().get(fieldIndex).type(), value));
    }
  }

  static void verifyMetadata(
      CoreProgram program, CoreAuthoringMap authoring, CoreMetadata metadata) {
    java.util.Set<ApplicationKey> applications = new java.util.HashSet<>();
    for (CoreAnnotationApplication application : metadata.annotations()) {
      verifyApplication(program, authoring, application);
      if (!applications.add(applicationKey(application))) {
        throw new IllegalArgumentException("annotation application must be unique per target");
      }
    }
  }

  private static void verifyApplication(
      CoreProgram program, CoreAuthoringMap authoring, CoreAnnotationApplication application) {
    DefinitionId annotationId = application.annotation();
    CoreDefinition definition =
        program
            .definition(annotationId)
            .orElseThrow(() -> new IllegalArgumentException("annotation definition is absent"));
    if (!(definition instanceof CoreDefinition.Annotation annotation)) {
      throw new IllegalArgumentException("annotation application must reference an annotation");
    }
    if (annotation.retention() == AnnotationRetention.SOURCE) {
      throw new IllegalArgumentException("source annotation cannot be stored in Core");
    }
    if (!annotation.targets().contains(application.target().kind())) {
      throw new IllegalArgumentException("annotation application target is not allowed");
    }
    verifyTarget(program, authoring, application.target());
    if (application.values().size() != annotation.fields().size()) {
      throw new IllegalArgumentException("annotation application values are not normalized");
    }
    for (int index = 0; index < application.values().size(); index++) {
      verifyValue(
          program,
          annotationId,
          annotation.fields().get(index).type(),
          application.values().get(index));
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
                  occurrence.role() == CoreDefinitionRole.ANNOTATION
                          && targetDefinition instanceof CoreDefinition.Annotation
                      || occurrence.role() == CoreDefinitionRole.AGGREGATE
                          && targetDefinition instanceof CoreDefinition.Aggregate
                      || occurrence.role() == CoreDefinitionRole.ENUM
                          && targetDefinition instanceof CoreDefinition.Enum
                      || occurrence.role() == CoreDefinitionRole.INTERFACE
                          && targetDefinition instanceof CoreDefinition.Interface;
              case CONSTRUCTOR ->
                  occurrence.role() == CoreDefinitionRole.CONSTRUCTOR
                      && targetDefinition instanceof CoreDefinition.Callable;
              case FUNCTION ->
                  occurrence.role() == CoreDefinitionRole.INTERFACE_METHOD
                          && targetDefinition instanceof CoreDefinition.InterfaceMethod
                      || (occurrence.role() == CoreDefinitionRole.FUNCTION
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
        if (occurrence.role() != CoreDefinitionRole.ANNOTATION
            && occurrence.role() != CoreDefinitionRole.AGGREGATE) {
          throw new IllegalArgumentException("annotation field target has the wrong role");
        }
        List<CoreField> fields =
            switch (targetDefinition) {
              case CoreDefinition.Annotation annotation -> annotation.fields();
              case CoreDefinition.Aggregate aggregate -> aggregate.fields();
              default ->
                  throw new IllegalArgumentException("annotation field target has no fields");
            };
        if (fields.stream().noneMatch(candidate -> candidate.ordinal() == field.ordinal())) {
          throw new IllegalArgumentException("annotation field target is outside its owner");
        }
      }
      case CoreAnnotationTarget.Parameter parameter -> {
        CoreDefinitionOccurrence occurrence = requireOccurrence(authoring, parameter.callable());
        if (occurrence.role() != CoreDefinitionRole.CONSTRUCTOR
            && occurrence.role() != CoreDefinitionRole.FUNCTION
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
      CoreProgram program, DefinitionId owner, CoreType expected, CoreAnnotationValue value) {
    CoreType absoluteExpected = CoreTypes.absolute(expected, owner, program);
    CoreType absoluteActual = CoreTypes.absolute(value.type(), owner, program);
    if (!absoluteExpected.equals(absoluteActual)) {
      throw new IllegalArgumentException("annotation value type does not match its field");
    }
    requireMetadataType(program, owner, value.type());
    Object raw = value.value();
    if (raw == null) {
      if (!value.type().isNullable()) {
        throw new IllegalArgumentException("null annotation value requires a nullable field");
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
      throw new IllegalArgumentException("annotation field type is not a metadata scalar");
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
