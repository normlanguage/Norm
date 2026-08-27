package dev.w0fv1.norm.semantic;

import java.util.Objects;

public record AnnotationParameterInfo(SymbolId symbol, String name, SemanticType type) {
  public AnnotationParameterInfo {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
  }
}
