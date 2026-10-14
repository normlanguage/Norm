package dev.w0fv1.norm.core;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CoreRuntimeSchema {
  private final CoreRuntimeIdentity identity;
  private final Map<DefinitionId, TypeContract> types;
  private final Map<String, CallableContract> callables;
  private final Set<String> constructors;
  private final Map<String, DefinitionId> fieldInitializers;

  private CoreRuntimeSchema(CoreArtifact artifact) {
    identity = CoreRuntimeIdentity.create(artifact);
    var indexedConstructors = new java.util.HashSet<String>();
    var indexedInitializers = new HashMap<String, DefinitionId>();
    var indexedCallables = new HashMap<String, CallableContract>();
    for (var occurrence : artifact.authoring().occurrences()) {
      var definition =
          identity.definition(
              occurrence.id().representative(),
              artifact.program().definition(occurrence.id().representative()).orElseThrow());
      CallableContract contract =
          switch (definition) {
            case CoreDefinition.Callable callable ->
                new CallableContract(
                    callable.receiverType(),
                    callable.typeParameters(),
                    callable.captureTypes(),
                    callable.parameters().stream()
                        .map(parameter -> new Parameter(parameter.name(), parameter.type()))
                        .toList(),
                    callable.returnType());
            case CoreDefinition.MethodSignature method ->
                new CallableContract(
                    Optional.of(method.receiverType()),
                    method.typeParameters(),
                    List.of(),
                    java.util.stream.IntStream.range(0, method.parameterTypes().size())
                        .mapToObj(
                            index -> new Parameter("$" + index, method.parameterTypes().get(index)))
                        .toList(),
                    method.returnType());
            default -> null;
          };
      if (contract != null) indexedCallables.put(identity.key(occurrence.id()), contract);
      if (occurrence.role() == CoreDefinitionRole.CONSTRUCTOR)
        indexedConstructors.add(identity.key(occurrence.id()));
      if (definition instanceof CoreDefinition.Aggregate aggregate) {
        for (var field : aggregate.fields()) {
          field
              .initializer()
              .ifPresent(
                  initializer -> {
                    var target =
                        artifact.authoring().target(occurrence.id(), initializer.nodeIndex());
                    indexedInitializers.put(
                        identity.key(target), identity.id(occurrence.id().representative()));
                  });
        }
      }
    }
    callables = Map.copyOf(indexedCallables);
    constructors = Set.copyOf(indexedConstructors);
    fieldInitializers = Map.copyOf(indexedInitializers);
    var indexedTypes = new HashMap<DefinitionId, TypeContract>();
    for (var record : identity.definitions()) {
      TypeContract contract =
          switch (record.definition()) {
            case CoreDefinition.Aggregate aggregate -> {
              Map<String, FieldContract> fields = new LinkedHashMap<>();
              aggregate
                  .fields()
                  .forEach(
                      field ->
                          fields.put(
                              field.name(), new FieldContract(field.visibility(), field.type())));
              yield new TypeContract(
                  switch (aggregate.kind()) {
                    case CLASS -> TypeKind.CLASS;
                    case VALUE -> TypeKind.VALUE;
                    case ANNOTATION -> TypeKind.ANNOTATION;
                  },
                  aggregate.valueCategory(),
                  aggregate.typeParameters(),
                  aggregate.parentType().stream().toList(),
                  Map.copyOf(fields),
                  Map.of(),
                  aggregate.conformances().stream()
                      .map(CoreConformance::interfaceType)
                      .collect(java.util.stream.Collectors.toSet()));
            }
            case CoreDefinition.Interface declared ->
                new TypeContract(
                    TypeKind.INTERFACE,
                    CoreValueCategory.POLYMORPHIC,
                    declared.typeParameters(),
                    declared.directParents(),
                    Map.of(),
                    Map.of(),
                    Set.of());
            case CoreDefinition.Enum declared ->
                new TypeContract(
                    TypeKind.ENUM,
                    CoreValueCategory.VALUE,
                    declared.typeParameters(),
                    List.of(),
                    Map.of(),
                    declared.variants().stream()
                        .collect(
                            java.util.stream.Collectors.toMap(
                                CoreEnumVariant::key, CoreEnumVariant::fields)),
                    Set.of());
            default -> null;
          };
      if (contract != null) indexedTypes.put(record.id(), contract);
    }
    types = Map.copyOf(indexedTypes);
  }

  public static CoreRuntimeSchema create(CoreArtifact artifact) {
    return new CoreRuntimeSchema(Objects.requireNonNull(artifact));
  }

  public CoreRuntimeIdentity identity() {
    return identity;
  }

  public void requireCompatible(CoreRuntimeSchema candidate) {
    requireCompatible(candidate, Set.of());
  }

  public void requireCompatible(CoreRuntimeSchema candidate, Set<DefinitionId> migrations) {
    Objects.requireNonNull(candidate);
    Objects.requireNonNull(migrations);
    types.forEach(
        (id, before) -> {
          TypeContract after = candidate.types.get(id);
          if (after == null) return;
          if (!before.kind().equals(after.kind())
              || before.category() != after.category()
              || !before.parameters().equals(after.parameters())
              || !after.parents().containsAll(before.parents())
              || !after.conformances().containsAll(before.conformances()))
            throw new IllegalArgumentException(
                "Runtime type contract requires an explicit migration: " + id);
          if (before.category() == CoreValueCategory.VALUE
              && !before.fields().equals(after.fields()))
            throw new IllegalArgumentException(
                "Runtime value layout requires collection key migration: " + id);
          if (!migrations.contains(id))
            before
                .fields()
                .forEach(
                    (name, field) -> {
                      if (!field.equals(after.fields().get(name)))
                        throw new IllegalArgumentException(
                            "Runtime field requires an explicit migration: " + name);
                    });
          before
              .variants()
              .forEach(
                  (name, variant) -> {
                    if (!variant.equals(after.variants().get(name)))
                      throw new IllegalArgumentException(
                          "Runtime enum variant requires an explicit migration: " + name);
                  });
        });
    callables.forEach(
        (key, before) -> {
          CallableContract after = candidate.callables.get(key);
          boolean constructor = constructors.contains(key) && candidate.constructors.contains(key);
          boolean compatibleConstructor =
              constructor
                  && after != null
                  && before.receiver().equals(after.receiver())
                  && before.parameters().equals(after.parameters())
                  && before.captures().equals(after.captures())
                  && before.result().equals(after.result());
          DefinitionId initializerOwner = fieldInitializers.get(key);
          boolean migratedInitializer =
              initializerOwner != null
                  && initializerOwner.equals(candidate.fieldInitializers.get(key))
                  && migrations.contains(initializerOwner)
                  && after != null
                  && before.receiver().equals(after.receiver())
                  && before.parameters().equals(after.parameters())
                  && before.captures().equals(after.captures())
                  && before.arguments().equals(after.arguments());
          if (after != null
              && !before.equals(after)
              && !compatibleConstructor
              && !migratedInitializer)
            throw new IllegalArgumentException(
                "Runtime callable contract requires an explicit migration: " + key);
        });
  }

  public CallableContract callableContract(DefinitionOccurrenceId occurrence) {
    return callables.get(identity.key(occurrence));
  }

  public record Parameter(String name, CoreType type) {}

  public record CallableContract(
      Optional<CoreType> receiver,
      List<CoreTypeParameter> parameters,
      List<CoreType> captures,
      List<Parameter> arguments,
      CoreType result) {}

  private record FieldContract(CoreVisibility visibility, CoreType type) {}

  private enum TypeKind {
    CLASS,
    VALUE,
    ANNOTATION,
    INTERFACE,
    ENUM
  }

  private record TypeContract(
      TypeKind kind,
      CoreValueCategory category,
      List<CoreTypeParameter> parameters,
      List<CoreType> parents,
      Map<String, FieldContract> fields,
      Map<String, List<CoreField>> variants,
      Set<CoreType> conformances) {}
}
