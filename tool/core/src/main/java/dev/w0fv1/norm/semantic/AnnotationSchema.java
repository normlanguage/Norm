package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.AnnotationRetention;
import dev.w0fv1.norm.value.AnnotationTarget;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AnnotationSchema(
    SymbolId symbol,
    String name,
    Set<AnnotationTarget> targets,
    AnnotationRetention retention,
    List<AnnotationParameterInfo> parameters) {
  public AnnotationSchema {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(name, "name");
    targets = Set.copyOf(targets);
    Objects.requireNonNull(retention, "retention");
    parameters = List.copyOf(parameters);
  }
}
