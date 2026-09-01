package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record AnnotationSchema(
    SymbolId symbol,
    String name,
    Set<AnnotationTarget> targets,
    AnnotationRetention retention,
    boolean repeatable,
    boolean inherited,
    Set<AnnotationTarget> interceptors,
    Map<AnnotationTarget, SemanticType> targetTypes,
    List<AnnotationParameterInfo> parameters) {
  public AnnotationSchema {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(name, "name");
    targets = Set.copyOf(targets);
    Objects.requireNonNull(retention, "retention");
    interceptors = Set.copyOf(interceptors);
    targetTypes = Map.copyOf(targetTypes);
    parameters = List.copyOf(parameters);
  }

  public Optional<SemanticType> targetType(AnnotationTarget target) {
    return Optional.ofNullable(targetTypes.get(target));
  }

  public boolean intercepts(AnnotationTarget target) {
    return interceptors.contains(target);
  }
}
