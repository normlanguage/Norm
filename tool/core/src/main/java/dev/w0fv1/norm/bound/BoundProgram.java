package dev.w0fv1.norm.bound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record BoundProgram(
    List<BoundSource> sources,
    List<BoundEnum> enums,
    List<BoundInterface> interfaces,
    List<BoundBuiltinConformance> builtinConformances,
    List<BoundClass> classes,
    List<BoundCallable> callables,
    Optional<BoundCallableId> entryPoint) {
  public BoundProgram {
    sources = List.copyOf(sources);
    enums = List.copyOf(enums);
    interfaces = List.copyOf(interfaces);
    builtinConformances = List.copyOf(builtinConformances);
    classes = List.copyOf(classes);
    callables = List.copyOf(callables);
    entryPoint = Objects.requireNonNull(entryPoint, "entryPoint");
  }

  public BoundCallable entryCallable() {
    return callables.stream()
        .filter(callable -> callable.id().equals(entryPoint.orElseThrow()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("entry callable is absent"));
  }

  public BoundProgram withEntryPoint(BoundCallableId entry) {
    return new BoundProgram(
        sources, enums, interfaces, builtinConformances, classes, callables, Optional.of(entry));
  }
}
