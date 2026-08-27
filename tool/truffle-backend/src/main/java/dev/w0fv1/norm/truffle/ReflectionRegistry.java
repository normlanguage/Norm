package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreAnnotationApplication;
import dev.w0fv1.norm.core.CoreAnnotationTarget;
import dev.w0fv1.norm.core.CoreAnnotationValue;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionRecord;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreProgram;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.value.AnnotationRetention;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ReflectionRegistry {
  private final CoreProgram program;
  private final Map<DefinitionId, RuntimeValues.AnnotationInfo> schemas;
  private final Map<DefinitionId, Map<DefinitionId, RuntimeValues.ObjectValue>> typeAnnotations;

  ReflectionRegistry(CoreArtifact artifact) {
    this.program = artifact.program();
    Map<DefinitionId, RuntimeValues.AnnotationInfo> indexedSchemas = new LinkedHashMap<>();
    for (CoreDefinitionRecord record : program.definitions()) {
      if (record.definition() instanceof CoreDefinition.Annotation annotation) {
        indexedSchemas.put(
            record.id(),
            new RuntimeValues.AnnotationInfo(
                record.id(), annotation.nominalType().name(), annotation.fields().size()));
      }
    }
    schemas = Map.copyOf(indexedSchemas);
    Map<DefinitionId, Map<DefinitionId, RuntimeValues.ObjectValue>> indexedApplications =
        new LinkedHashMap<>();
    for (CoreAnnotationApplication application : artifact.metadata().annotations()) {
      if (!(application.target() instanceof CoreAnnotationTarget.Definition target)
          || target.kind() != dev.w0fv1.norm.value.AnnotationTarget.TYPE) {
        continue;
      }
      DefinitionId annotationId = application.annotation();
      CoreDefinition.Annotation annotation =
          (CoreDefinition.Annotation) program.definition(annotationId).orElseThrow();
      if (annotation.retention() != AnnotationRetention.RUNTIME) continue;
      DefinitionId targetId = target.occurrence().representative();
      RuntimeValues.ObjectValue value = annotationValue(annotationId, application.values());
      RuntimeValues.ObjectValue previous =
          indexedApplications
              .computeIfAbsent(targetId, ignored -> new LinkedHashMap<>())
              .putIfAbsent(annotationId, value);
      if (previous != null) {
        throw new IllegalStateException("verified runtime annotation is duplicated");
      }
    }
    Map<DefinitionId, Map<DefinitionId, RuntimeValues.ObjectValue>> frozen = new LinkedHashMap<>();
    indexedApplications.forEach((target, values) -> frozen.put(target, Map.copyOf(values)));
    typeAnnotations = Map.copyOf(frozen);
  }

  String name(CoreType type) {
    return displayName(type);
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

  Object annotation(CoreType reflectedType, CoreType annotationType) {
    CoreType.Declared reflected = declared(reflectedType);
    if (!(reflected.constructor() instanceof CoreTypeConstructor.User reflectedUser)) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    CoreType.Declared annotation = declared(annotationType);
    if (!(annotation.constructor() instanceof CoreTypeConstructor.User annotationUser)) {
      return RuntimeValues.NullValue.INSTANCE;
    }
    RuntimeValues.ObjectValue result =
        typeAnnotations
            .getOrDefault(resolveExternal(reflectedUser.definition()), Map.of())
            .get(resolveExternal(annotationUser.definition()));
    return result == null ? RuntimeValues.NullValue.INSTANCE : result;
  }

  private RuntimeValues.ObjectValue annotationValue(
      DefinitionId annotationId, List<CoreAnnotationValue> values) {
    CoreType type =
        new CoreType.Declared(
            new CoreTypeConstructor.User(new DefinitionReference.External(annotationId)),
            List.of(),
            CoreValueCategory.VALUE,
            CoreNullability.NON_NULL);
    RuntimeValues.ObjectValue result =
        new RuntimeValues.ObjectValue(schemas.get(annotationId), type);
    for (int index = 0; index < values.size(); index++) {
      Object value = values.get(index).value();
      result.fields[index] =
          value == null
              ? RuntimeValues.NullValue.INSTANCE
              : isCodePoint(values.get(index).type())
                  ? new RuntimeValues.CodePointValue((Integer) value)
                  : value;
    }
    return result;
  }

  private String nominal(DefinitionId id) {
    return switch (program.definition(id).orElseThrow()) {
      case CoreDefinition.Annotation annotation -> annotation.nominalType().name();
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

  private static boolean isCodePoint(CoreType type) {
    return type instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.Builtin builtin
        && builtin.id().value().equals("std.core.CodePoint");
  }
}
