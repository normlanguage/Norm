package dev.w0fv1.norm.value;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ModuleGraph(Map<ModuleCoordinate, Set<ModuleCoordinate>> dependencies) {
  public ModuleGraph {
    Map<ModuleCoordinate, Set<ModuleCoordinate>> stable = new LinkedHashMap<>();
    Objects.requireNonNull(dependencies, "dependencies").entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry -> {
              Set<ModuleCoordinate> required =
                  Objects.requireNonNull(entry.getValue(), "module dependencies").stream()
                      .map(value -> Objects.requireNonNull(value, "module dependency"))
                      .sorted()
                      .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
              stable.put(
                  Objects.requireNonNull(entry.getKey(), "module"),
                  java.util.Collections.unmodifiableSet(required));
            });
    for (Set<ModuleCoordinate> required : stable.values()) {
      if (!stable.keySet().containsAll(required)) {
        throw new IllegalArgumentException("module dependencies must belong to the graph");
      }
    }
    dependencies = java.util.Collections.unmodifiableMap(stable);
  }

  public static ModuleGraph isolated(Collection<ModuleCoordinate> modules) {
    Map<ModuleCoordinate, Set<ModuleCoordinate>> dependencies = new LinkedHashMap<>();
    Objects.requireNonNull(modules, "modules").stream()
        .sorted()
        .forEach(module -> dependencies.put(module, Set.of()));
    return new ModuleGraph(dependencies);
  }

  public boolean canRead(ModuleCoordinate source, ModuleCoordinate target) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(target, "target");
    return source.equals(target) || dependencies.getOrDefault(source, Set.of()).contains(target);
  }

  public ModuleGraph merge(ModuleGraph other) {
    Objects.requireNonNull(other, "other");
    Map<ModuleCoordinate, Set<ModuleCoordinate>> merged = new LinkedHashMap<>(dependencies);
    for (Map.Entry<ModuleCoordinate, Set<ModuleCoordinate>> entry : other.dependencies.entrySet()) {
      Set<ModuleCoordinate> previous = merged.putIfAbsent(entry.getKey(), entry.getValue());
      if (previous != null && !previous.equals(entry.getValue())) {
        throw new IllegalArgumentException(
            "module graph defines conflicting dependencies for " + entry.getKey());
      }
    }
    return new ModuleGraph(merged);
  }

  public ModuleGraph withReads(
      Collection<ModuleCoordinate> readers, Collection<ModuleCoordinate> targets) {
    Map<ModuleCoordinate, Set<ModuleCoordinate>> expanded = new LinkedHashMap<>(dependencies);
    for (ModuleCoordinate reader : readers) {
      if (!expanded.containsKey(reader)) {
        throw new IllegalArgumentException("reader module is outside the graph");
      }
      LinkedHashSet<ModuleCoordinate> readable =
          new LinkedHashSet<>(expanded.getOrDefault(reader, Set.of()));
      for (ModuleCoordinate target : targets) {
        if (!expanded.containsKey(target)) {
          throw new IllegalArgumentException("target module is outside the graph");
        }
        if (!reader.equals(target)) readable.add(target);
      }
      expanded.put(reader, Set.copyOf(readable));
    }
    return new ModuleGraph(expanded);
  }

  public Set<ModuleCoordinate> modules() {
    return dependencies.keySet();
  }
}
