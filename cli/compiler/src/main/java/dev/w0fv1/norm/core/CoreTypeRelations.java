package dev.w0fv1.norm.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CoreTypeRelations {
  private final Map<DefinitionId, CoreDefinition> definitions;
  private final Set<DefinitionId> knownDefinitions;
  private final List<CoreDefinitionRecord> builtinConformances;

  public CoreTypeRelations(List<CoreDefinitionRecord> records) {
    knownDefinitions =
        records.stream()
            .map(CoreDefinitionRecord::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    var definitions = new LinkedHashMap<DefinitionId, CoreDefinition>();
    var conformances = new ArrayList<CoreDefinitionRecord>();
    for (var record : records) {
      if (record.definition() instanceof CoreDefinition.Aggregate
          || record.definition() instanceof CoreDefinition.Interface) {
        definitions.put(record.id(), record.definition());
      } else if (record.definition() instanceof CoreDefinition.BuiltinConformance) {
        conformances.add(record);
      }
    }
    this.definitions = Map.copyOf(definitions);
    builtinConformances = List.copyOf(conformances);
  }

  public boolean isAssignable(CoreType expected, CoreType actual) {
    if (expected.equals(CoreType.DYNAMIC) || actual.equals(CoreType.DYNAMIC)) return true;
    if (expected.equals(CoreType.EXISTENTIAL) || actual.equals(CoreType.EXISTENTIAL)) {
      return expected.equals(actual);
    }
    if (actual.equals(CoreType.NULL)) return expected.isNullable();
    if (actual.isNullable() && !expected.isNullable()) return false;
    CoreType target = nonNullable(expected);
    CoreType source = nonNullable(actual);
    if (target.equals(CoreType.ANY)) return true;
    if (matches(target, source)) return true;
    if (target.equals(CoreType.NUMBER)
        && List.of(CoreType.INTEGER, CoreType.LONG, CoreType.FLOAT, CoreType.DOUBLE)
            .contains(source)) return true;
    if (target instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user
        && user.definition() instanceof DefinitionReference.External external) {
      var view = view(source, external.definition());
      return view != null && matches(target, view);
    }
    return false;
  }

  public CoreType.Declared view(CoreType actual, DefinitionId target) {
    var pending = new ArrayDeque<CoreType>();
    var visited = new HashSet<CoreType>();
    pending.add(nonNullable(actual));
    while (!pending.isEmpty()) {
      CoreType type = pending.removeFirst();
      if (!visited.add(type) || !(type instanceof CoreType.Declared declared)) continue;
      if (declared.constructor() instanceof CoreTypeConstructor.User user
          && user.definition() instanceof DefinitionReference.External external) {
        DefinitionId id = external.definition();
        if (id.equals(target)) return declared;
        CoreDefinition definition = definitions.get(id);
        List<CoreType> parents = new ArrayList<>();
        if (definition instanceof CoreDefinition.Aggregate aggregate) {
          aggregate.parentType().ifPresent(parents::add);
          aggregate.conformances().forEach(conformance -> parents.add(conformance.interfaceType()));
        } else if (definition instanceof CoreDefinition.Interface contract) {
          parents.addAll(contract.directParents());
        }
        for (CoreType parent : parents) {
          pending.add(absolute(parent, id).substitute(declared.arguments()::get));
        }
      } else if (declared.constructor() instanceof CoreTypeConstructor.Builtin) {
        for (var record : builtinConformances) {
          var conformance = (CoreDefinition.BuiltinConformance) record.definition();
          Map<Integer, CoreType> substitutions = new LinkedHashMap<>();
          if (matchTemplate(
              absolute(conformance.concreteBuiltinType(), record.id()), declared, substitutions)) {
            pending.add(
                absolute(conformance.interfaceType(), record.id()).substitute(substitutions::get));
          }
        }
      }
    }
    return null;
  }

  private CoreType absolute(CoreType type, DefinitionId owner) {
    return CoreTypes.absolute(
        type,
        owner,
        (context, reference) -> reference.resolve(context, knownDefinitions::contains));
  }

  private static boolean matches(CoreType expected, CoreType actual) {
    if (expected.equals(actual)) return true;
    if (expected instanceof CoreType.Function function && actual instanceof CoreType.Function) {
      return function.returnType().equals(CoreType.EXISTENTIAL)
          && function.parameterTypes().isEmpty();
    }
    if (!(expected instanceof CoreType.Declared left)
        || !(actual instanceof CoreType.Declared right)
        || !left.constructor().equals(right.constructor())
        || left.category() != right.category()
        || left.arguments().size() != right.arguments().size()) return false;
    for (int index = 0; index < left.arguments().size(); index++) {
      if (!left.arguments().get(index).equals(CoreType.EXISTENTIAL)
          && !left.arguments().get(index).equals(right.arguments().get(index))) return false;
    }
    return true;
  }

  private static boolean matchTemplate(
      CoreType template, CoreType actual, Map<Integer, CoreType> substitutions) {
    if (template instanceof CoreType.Parameter parameter) {
      CoreType value = parameter.isNullable() ? nonNullable(actual) : actual;
      var previous = substitutions.putIfAbsent(parameter.index(), value);
      return previous == null || previous.equals(value);
    }
    if (!(template instanceof CoreType.Declared left)
        || !(actual instanceof CoreType.Declared right)
        || !left.constructor().equals(right.constructor())
        || left.category() != right.category()
        || left.nullability() != right.nullability()
        || left.arguments().size() != right.arguments().size()) {
      return template.equals(actual);
    }
    for (int index = 0; index < left.arguments().size(); index++) {
      if (!matchTemplate(left.arguments().get(index), right.arguments().get(index), substitutions))
        return false;
    }
    return true;
  }

  private static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared value ->
          new CoreType.Declared(
              value.constructor(), value.arguments(), value.category(), CoreNullability.NON_NULL);
      case CoreType.Function value ->
          new CoreType.Function(
              value.returnType(), value.parameterTypes(), CoreNullability.NON_NULL);
      case CoreType.Parameter value ->
          new CoreType.Parameter(value.index(), CoreNullability.NON_NULL);
      default -> type;
    };
  }
}
