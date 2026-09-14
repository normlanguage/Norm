package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundLocalId;
import dev.w0fv1.norm.core.CoreBindingShape;
import dev.w0fv1.norm.core.CoreCompilationInput;
import dev.w0fv1.norm.core.CoreDefaultArgument;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import dev.w0fv1.norm.core.PendingDefinitionReference;
import dev.w0fv1.norm.semantic.SemanticType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
      Map<Integer, Key> relocations) {
    Unit {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(binding, "binding");
      relocations = Map.copyOf(relocations);
    }

    static Unit capture(
        CoreCompilationInput.Source source,
        Optional<CoreBindingShape> binding,
        Map<Integer, Key> declarations) {
      var references = new LinkedHashSet<>(source.references());
      binding.ifPresent(
          shape ->
              shape.mapLinks(
                  link -> {
                    if (link instanceof PendingDefinitionReference pending)
                      references.add(pending.declarationIndex());
                    return link;
                  },
                  value -> {
                    references.add(((CoreDefaultArgument.Pending) value).declarationIndex());
                    return value;
                  }));
      var relocations = new LinkedHashMap<Integer, Key>();
      references.forEach(
          index ->
              relocations.put(
                  index, Objects.requireNonNull(declarations.get(index), "relocation target")));
      return new Unit(source, binding, relocations);
    }

    int declarationIndex(int index, Map<Key, Integer> declarations) {
      return Objects.requireNonNull(
          declarations.get(relocations.get(index)), "relocated declaration");
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
