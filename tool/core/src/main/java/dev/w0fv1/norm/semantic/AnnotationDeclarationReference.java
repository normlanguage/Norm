package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.Objects;

public record AnnotationDeclarationReference(
    Kind kind, SymbolId target, SemanticType actualType, SourceSpan span)
    implements AnnotationValue.Content {
  public AnnotationDeclarationReference {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(actualType, "actualType");
    Objects.requireNonNull(span, "span");
  }

  public enum Kind {
    CLASS,
    CALLABLE,
    FIELD
  }
}
