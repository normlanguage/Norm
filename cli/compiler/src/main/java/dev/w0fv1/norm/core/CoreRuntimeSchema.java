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

  private CoreRuntimeSchema(CoreArtifact artifact) {
    identity = CoreRuntimeIdentity.create(artifact);
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
                    callable.captureLocals().stream()
                        .map(local -> occurrence.origin().localNames().get(local))
                        .toList(),
                    callable.parameters().stream()
                        .map(parameter -> new Parameter(parameter.name(), parameter.type()))
                        .toList(),
                    callable.returnType());
            case CoreDefinition.MethodSignature method ->
                new CallableContract(
                    Optional.of(method.receiverType()),
                    method.typeParameters(),
                    List.of(),
                    List.of(),
                    java.util.stream.IntStream.range(0, method.parameterTypes().size())
                        .mapToObj(
                            index -> new Parameter("$" + index, method.parameterTypes().get(index)))
                        .toList(),
                    method.returnType());
            default -> null;
          };
      if (contract != null) indexedCallables.put(identity.key(occurrence.id()), contract);
    }
    callables = Map.copyOf(indexedCallables);
    var definitions =
        identity.definitions().stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    CoreDefinitionRecord::id, CoreDefinitionRecord::definition));
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
                  aggregate.typeParameters().stream()
                      .map(
                          parameter ->
                              new CoreTypeParameter(parameter.index(), parameter.upperBound()))
                      .toList(),
                  aggregate.parentType().stream().toList(),
                  Map.copyOf(fields),
                  Map.of(),
                  aggregate.conformances().stream()
                      .map(CoreConformance::interfaceType)
                      .collect(java.util.stream.Collectors.toSet()),
                  Set.of());
            }
            case CoreDefinition.Interface declared ->
                new TypeContract(
                    TypeKind.INTERFACE,
                    CoreValueCategory.POLYMORPHIC,
                    declared.typeParameters().stream()
                        .map(
                            parameter ->
                                new CoreTypeParameter(parameter.index(), parameter.upperBound()))
                        .toList(),
                    declared.directParents(),
                    Map.of(),
                    Map.of(),
                    Set.of(),
                    declared.declaredMethods().stream()
                        .map(
                            link -> {
                              var definition =
                                  definitions.get(
                                      ((DefinitionReference.External) link).definition());
                              if (definition instanceof CoreDefinition.MethodSignature method)
                                return new MethodContract(
                                    method.name(),
                                    method.typeParameters().stream()
                                        .map(
                                            parameter ->
                                                new CoreTypeParameter(
                                                    parameter.index(), parameter.upperBound()))
                                        .toList(),
                                    method.parameterTypes(),
                                    method.returnType());
                              throw new IllegalStateException(
                                  "Interface member has no method signature");
                            })
                        .collect(java.util.stream.Collectors.toSet()));
            case CoreDefinition.Enum declared ->
                new TypeContract(
                    TypeKind.ENUM,
                    CoreValueCategory.VALUE,
                    declared.typeParameters().stream()
                        .map(
                            parameter ->
                                new CoreTypeParameter(parameter.index(), parameter.upperBound()))
                        .toList(),
                    List.of(),
                    Map.of(),
                    declared.variants().stream()
                        .collect(
                            java.util.stream.Collectors.toMap(
                                CoreEnumVariant::key,
                                variant ->
                                    variant.fields().stream()
                                        .map(field -> new Parameter(field.name(), field.type()))
                                        .toList())),
                    Set.of(),
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
          if (!before.kind().equals(after.kind()) || before.category() != after.category())
            throw new IllegalArgumentException("Runtime nominal kind cannot change: " + id);
          if (!migrations.contains(id)
              && (requiresTypeParameterMigration(before.parameters(), after.parameters())
                  || !after.parents().containsAll(before.parents())
                  || !after.conformances().containsAll(before.conformances())))
            throw new IllegalArgumentException(
                "Runtime type contract requires an explicit migration: " + id);
          if (!migrations.contains(id))
            before
                .fields()
                .forEach(
                    (name, field) -> {
                      if (!field.equals(after.fields().get(name))
                          && !(after.fields().get(name) != null
                              && field.visibility() == after.fields().get(name).visibility()
                              && migratableFieldType(
                                  field.type(), after.fields().get(name).type())))
                        throw new IllegalArgumentException(
                            "Runtime field requires an explicit migration: " + name);
                    });
          if (!migrations.contains(id))
            before
                .variants()
                .forEach(
                    (name, variant) -> {
                      if (!variant.equals(after.variants().get(name)))
                        throw new IllegalArgumentException(
                            "Runtime enum variant requires an explicit migration: " + name);
                    });
        });
  }

  public static boolean requiresTypeParameterMigration(
      List<CoreTypeParameter> source, List<CoreTypeParameter> target) {
    return source.size() != target.size()
        || java.util.stream.IntStream.range(0, source.size())
            .anyMatch(
                index ->
                    target.get(index).upperBound().isPresent()
                        && !source.get(index).upperBound().equals(target.get(index).upperBound()));
  }

  public static boolean migratableFieldType(CoreType source, CoreType target) {
    if (source.equals(target)) return true;
    if (!(source instanceof CoreType.Declared before)
        || !(target instanceof CoreType.Declared after)
        || !before.constructor().equals(after.constructor())
        || before.category() != after.category()
        || before.nullability() != after.nullability()) return false;
    if (before.constructor() instanceof CoreTypeConstructor.User) return true;
    var name = ((CoreTypeConstructor.Builtin) before.constructor()).id().value();
    return Set.of(
                "std.core.List",
                "std.core.Map",
                "std.core.Set",
                "std.core.Array",
                "std.core.Stack",
                "std.core.Queue",
                "std.core.Deque",
                "std.core.Pair")
            .contains(name)
        && before.arguments().size() == after.arguments().size()
        && java.util.stream.IntStream.range(0, before.arguments().size())
            .allMatch(
                index ->
                    migratableFieldType(
                        before.arguments().get(index), after.arguments().get(index)));
  }

  public TypeFingerprint typeFingerprint(CoreType type) {
    var contracts = new LinkedHashMap<DefinitionId, TypeContract>();
    var pending = new java.util.ArrayDeque<CoreType>();
    pending.add(type);
    while (!pending.isEmpty()) {
      var current = pending.removeFirst();
      switch (current) {
        case CoreType.Declared declared -> {
          pending.addAll(declared.arguments());
          if (declared.constructor() instanceof CoreTypeConstructor.User user
              && user.definition() instanceof DefinitionReference.External external) {
            var contract = types.get(external.definition());
            if (contract == null || contracts.putIfAbsent(external.definition(), contract) != null)
              continue;
            contract
                .parameters()
                .forEach(parameter -> parameter.upperBound().ifPresent(pending::add));
            pending.addAll(contract.parents());
            pending.addAll(contract.conformances());
            contract.fields().values().forEach(field -> pending.add(field.type()));
            contract
                .variants()
                .values()
                .forEach(fields -> fields.forEach(field -> pending.add(field.type())));
            contract
                .methods()
                .forEach(
                    method -> {
                      method
                          .parameters()
                          .forEach(parameter -> parameter.upperBound().ifPresent(pending::add));
                      pending.addAll(method.arguments());
                      pending.add(method.result());
                    });
          }
        }
        case CoreType.Function function -> {
          pending.add(function.returnType());
          pending.addAll(function.parameterTypes());
        }
        case CoreType.Reference reference -> pending.add(reference.target());
        default -> {}
      }
    }
    return new TypeFingerprint(type, Map.copyOf(contracts));
  }

  public record TypeFingerprint(CoreType type, Map<DefinitionId, TypeContract> contracts) {
    public TypeFingerprint {
      contracts = Map.copyOf(contracts);
    }
  }

  public record MethodContract(
      String name, List<CoreTypeParameter> parameters, List<CoreType> arguments, CoreType result) {}

  public CallableContract callableContract(DefinitionOccurrenceId occurrence) {
    return callables.get(identity.key(occurrence));
  }

  public record Parameter(String name, CoreType type) {}

  public record CallableContract(
      Optional<CoreType> receiver,
      List<CoreTypeParameter> parameters,
      List<CoreType> captures,
      List<String> captureIdentities,
      List<Parameter> arguments,
      CoreType result) {}

  public record FieldContract(CoreVisibility visibility, CoreType type) {}

  public enum TypeKind {
    CLASS,
    VALUE,
    ANNOTATION,
    INTERFACE,
    ENUM
  }

  public record TypeContract(
      TypeKind kind,
      CoreValueCategory category,
      List<CoreTypeParameter> parameters,
      List<CoreType> parents,
      Map<String, FieldContract> fields,
      Map<String, List<Parameter>> variants,
      Set<CoreType> conformances,
      Set<MethodContract> methods) {
    public TypeContract {
      parameters = List.copyOf(parameters);
      parents = List.copyOf(parents);
      fields = Map.copyOf(fields);
      variants = Map.copyOf(variants);
      conformances = Set.copyOf(conformances);
      methods = Set.copyOf(methods);
    }
  }
}
