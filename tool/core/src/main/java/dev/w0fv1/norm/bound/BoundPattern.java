package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public sealed interface BoundPattern extends BoundNode
    permits BoundPattern.Variant,
        BoundPattern.Binding,
        BoundPattern.Wildcard,
        BoundPattern.Literal,
        BoundPattern.Null {
  record Variant(
      BoundEnumId enumId, String variantKey, List<BoundPattern> arguments, SourceSpan span)
      implements BoundPattern {
    public Variant {
      Objects.requireNonNull(enumId, "enumId");
      Objects.requireNonNull(variantKey, "variantKey");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(span, "span");
    }
  }

  record Binding(BoundLocalId local, SemanticType type, SourceSpan span) implements BoundPattern {
    public Binding {
      Objects.requireNonNull(local, "local");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record Wildcard(SourceSpan span) implements BoundPattern {
    public Wildcard {
      Objects.requireNonNull(span, "span");
    }
  }

  record Literal(Object value, SemanticType type, SourceSpan span) implements BoundPattern {
    public Literal {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record Null(SemanticType type, SourceSpan span) implements BoundPattern {
    public Null {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }
}
