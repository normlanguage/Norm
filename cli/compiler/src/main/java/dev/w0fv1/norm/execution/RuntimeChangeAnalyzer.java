package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.CoreAnnotationTarget;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreDefinitionOccurrence;
import dev.w0fv1.norm.core.CoreDefinitionRole;
import dev.w0fv1.norm.core.CoreEnumVariant;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreTypeParameter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RuntimeChangeAnalyzer {
  public RuntimeChangeSet compare(CoreArtifact before, CoreArtifact after) {
    Objects.requireNonNull(before, "before");
    Objects.requireNonNull(after, "after");
    Map<String, Entry> previous = entries(before);
    Map<String, Entry> current = entries(after);
    List<RuntimeDeclarationChange> changes = new ArrayList<>();
    previous.forEach(
        (identity, oldEntry) -> {
          Entry newEntry = current.get(identity);
          if (newEntry == null) {
            changes.add(
                new RuntimeDeclarationChange(
                    RuntimeChangeKind.REMOVED,
                    oldEntry.shape().kind(),
                    identity,
                    Optional.of(oldEntry.shape()),
                    Optional.empty(),
                    oldEntry.source(),
                    oldEntry.startOffset(),
                    oldEntry.endOffset()));
          } else if (!oldEntry.shape().equals(newEntry.shape())) {
            changes.add(
                new RuntimeDeclarationChange(
                    RuntimeChangeKind.MODIFIED,
                    newEntry.shape().kind(),
                    identity,
                    Optional.of(oldEntry.shape()),
                    Optional.of(newEntry.shape()),
                    newEntry.source(),
                    newEntry.startOffset(),
                    newEntry.endOffset()));
          }
        });
    current.forEach(
        (identity, entry) -> {
          if (!previous.containsKey(identity)) {
            changes.add(
                new RuntimeDeclarationChange(
                    RuntimeChangeKind.ADDED,
                    entry.shape().kind(),
                    identity,
                    Optional.empty(),
                    Optional.of(entry.shape()),
                    entry.source(),
                    entry.startOffset(),
                    entry.endOffset()));
          }
        });
    changes.sort(
        Comparator.comparing(RuntimeDeclarationChange::source)
            .thenComparingInt(RuntimeDeclarationChange::startOffset)
            .thenComparing(RuntimeDeclarationChange::identity));
    return new RuntimeChangeSet(changes);
  }

  private Map<String, Entry> entries(CoreArtifact artifact) {
    Map<String, Entry> entries = new LinkedHashMap<>();
    for (CoreDefinitionOccurrence occurrence : artifact.authoring().occurrences()) {
      RuntimeDeclarationKind kind = kind(occurrence.role());
      if (kind == null) continue;
      CoreDefinition definition =
          artifact.program().definition(occurrence.id().representative()).orElseThrow();
      String identity =
          artifact
              .authoring()
              .origin(occurrence.id())
              .declarationIdentity()
              .orElse(artifact.authoring().origin(occurrence.id()).definitionName());
      if (entries.containsKey(identity)) identity = identity + "#" + occurrence.id().ordinal();
      var origin = artifact.authoring().origin(occurrence.id());
      var span = origin.rootSpan();
      var shape = shape(artifact, occurrence, definition, kind, identity);
      entries.put(
          identity,
          new Entry(
              shape, span.source().id().uri().toString(), span.startOffset(), span.endOffset()));
    }
    return Map.copyOf(entries);
  }

  private RuntimeDeclarationShape shape(
      CoreArtifact artifact,
      CoreDefinitionOccurrence occurrence,
      CoreDefinition definition,
      RuntimeDeclarationKind kind,
      String identity) {
    List<String> members = new ArrayList<>();
    String signature;
    switch (definition) {
      case CoreDefinition.Aggregate aggregate -> {
        signature =
            aggregate.kind().name().toLowerCase()
                + " "
                + aggregate.nominalType().name()
                + typeParameters(aggregate.typeParameters());
        aggregate.fields().forEach(field -> members.add(field.name() + ": " + type(field.type())));
      }
      case CoreDefinition.Enum enumeration -> {
        signature =
            "enum "
                + enumeration.nominalType().name()
                + typeParameters(enumeration.typeParameters());
        enumeration.variants().forEach(variant -> members.add(variant(variant)));
      }
      case CoreDefinition.Interface interfaceDefinition -> {
        signature =
            "interface "
                + interfaceDefinition.nominalType().name()
                + typeParameters(interfaceDefinition.typeParameters());
        interfaceDefinition
            .directParents()
            .forEach(parent -> members.add("extends " + type(parent)));
        interfaceDefinition.declaredMethods().forEach(method -> members.add(method.toString()));
      }
      case CoreDefinition.Callable callable -> {
        signature = callable(callable);
        members.addAll(callable.locals().stream().map(local -> local.type().toString()).toList());
      }
      case CoreDefinition.MethodSignature method -> {
        signature =
            "method "
                + method.name()
                + typeParameters(method.typeParameters())
                + parameters(method.parameterTypes())
                + ": "
                + type(method.returnType());
      }
      case CoreDefinition.BuiltinConformance ignored -> signature = "builtin-conformance";
    }
    return new RuntimeDeclarationShape(
        kind,
        identity,
        signature,
        digest(definition.toString()),
        artifact.authoring().origin(occurrence.id()).rootSpan().text(),
        members,
        annotations(artifact, occurrence));
  }

  private List<String> annotations(CoreArtifact artifact, CoreDefinitionOccurrence occurrence) {
    return artifact.metadata().annotations().stream()
        .filter(
            annotation ->
                annotation.target() instanceof CoreAnnotationTarget.Definition definition
                    && definition.occurrence().equals(occurrence.id()))
        .map(annotation -> annotation.annotation().toString())
        .sorted()
        .toList();
  }

  private static RuntimeDeclarationKind kind(CoreDefinitionRole role) {
    return switch (role) {
      case AGGREGATE -> RuntimeDeclarationKind.AGGREGATE;
      case ENUM -> RuntimeDeclarationKind.ENUM;
      case INTERFACE -> RuntimeDeclarationKind.INTERFACE;
      case FUNCTION -> RuntimeDeclarationKind.FUNCTION;
      case EXTENSION -> RuntimeDeclarationKind.EXTENSION;
      case METHOD -> RuntimeDeclarationKind.METHOD;
      case CONSTRUCTOR -> RuntimeDeclarationKind.CONSTRUCTOR;
      case METHOD_SIGNATURE -> RuntimeDeclarationKind.METHOD_SIGNATURE;
      case BUILTIN_CONFORMANCE, LAMBDA, DEFAULT_VALUE -> null;
    };
  }

  private static String callable(CoreDefinition.Callable callable) {
    String receiver = callable.receiverType().map(type -> type(type) + ".").orElse("");
    return receiver
        + typeParameters(callable.typeParameters())
        + parameters(callable.parameterTypes())
        + ": "
        + type(callable.returnType());
  }

  private static String parameters(List<CoreType> parameters) {
    return parameters.stream()
        .map(RuntimeChangeAnalyzer::type)
        .collect(java.util.stream.Collectors.joining(", ", "(", ")"));
  }

  private static String typeParameters(List<CoreTypeParameter> parameters) {
    if (parameters.isEmpty()) return "";
    return parameters.stream()
        .map(parameter -> "T" + parameter.index())
        .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
  }

  private static String variant(CoreEnumVariant variant) {
    return variant.key()
        + variant.fields().stream()
            .map(field -> field.name() + ": " + type(field.type()))
            .collect(java.util.stream.Collectors.joining(", ", "(", ")"));
  }

  private static String type(CoreType value) {
    return switch (value) {
      case CoreType.Declared declared -> {
        String name =
            switch (declared.constructor()) {
              case CoreTypeConstructor.Builtin builtin -> builtin.id().value();
              case CoreTypeConstructor.User user -> user.definition().toString();
            };
        String arguments =
            declared.arguments().isEmpty()
                ? ""
                : declared.arguments().stream()
                    .map(RuntimeChangeAnalyzer::type)
                    .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
        yield name + arguments + (declared.isNullable() ? "?" : "");
      }
      case CoreType.Function function ->
          "Function<" + type(function.returnType()) + parameters(function.parameterTypes()) + ">";
      case CoreType.Parameter parameter ->
          "T" + parameter.index() + (parameter.isNullable() ? "?" : "");
      case CoreType.Reference reference -> "ref " + type(reference.target());
      case CoreType.Special special -> special.kind().name().toLowerCase();
    };
  }

  private static String digest(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new AssertionError(exception);
    }
  }

  private record Entry(
      RuntimeDeclarationShape shape, String source, int startOffset, int endOffset) {}
}
