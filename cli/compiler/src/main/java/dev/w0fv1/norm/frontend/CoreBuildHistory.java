package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundLocalId;
import dev.w0fv1.norm.core.CoreBindingShape;
import dev.w0fv1.norm.core.CoreCompilationInput;
import dev.w0fv1.norm.core.CoreDefaultArgument;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.PendingDefinitionReference;
import dev.w0fv1.norm.semantic.SemanticType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

record CoreBuildHistory(
    Map<Key, DefinitionOccurrenceId> occurrences,
    Map<String, Map<BoundLocalId, Integer>> callableLocals,
    Map<Key, Unit> units) {
  CoreBuildHistory {
    units = Map.copyOf(units);
    occurrences = Map.copyOf(occurrences);
    callableLocals =
        callableLocals.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
  }

  record Unit(
      CoreCompilationInput.Source source,
      Optional<CoreBindingShape> binding,
      List<Key> declarations) {
    Unit {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(binding, "binding");
      declarations = List.copyOf(declarations);
    }

    static Unit capture(
        CoreCompilationInput.Source source,
        Optional<CoreBindingShape> binding,
        Map<Integer, Key> declarations) {
      var indices = new LinkedHashMap<Integer, Integer>();
      java.util.function.IntUnaryOperator local =
          index -> indices.computeIfAbsent(index, ignored -> indices.size());
      var relocated = source.relocate(local);
      var shape =
          binding.map(
              value ->
                  value.mapLinks(
                      link ->
                          link instanceof PendingDefinitionReference pending
                              ? new PendingDefinitionReference(
                                  local.applyAsInt(pending.declarationIndex()))
                              : link,
                      argument ->
                          argument instanceof CoreDefaultArgument.Pending pending
                              ? new CoreDefaultArgument.Pending(
                                  local.applyAsInt(pending.declarationIndex()))
                              : argument));
      return new Unit(
          relocated,
          shape,
          indices.keySet().stream()
              .map(index -> Objects.requireNonNull(declarations.get(index), "relocation target"))
              .toList());
    }

    int declarationIndex(int index, Map<Key, Integer> declarations) {
      return Objects.requireNonNull(
          declarations.get(this.declarations.get(index)), "relocated declaration");
    }
  }

  sealed interface Key {
    record Named(String identity) implements Key {
      public Named {
        Objects.requireNonNull(identity, "identity");
      }
    }

    record Conformance(SemanticType concreteType, SemanticType interfaceType) implements Key {
      public Conformance {
        Objects.requireNonNull(concreteType, "concreteType");
        Objects.requireNonNull(interfaceType, "interfaceType");
      }
    }
  }
}
