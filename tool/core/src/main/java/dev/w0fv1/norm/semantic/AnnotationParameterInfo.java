package dev.w0fv1.norm.semantic;

import java.util.Objects;
import java.util.Optional;

public record AnnotationParameterInfo(
    SymbolId symbol, String name, SemanticType type, Optional<AnnotationValue> defaultValue) {
  public AnnotationParameterInfo {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
  }
}
