package dev.w0fv1.norm.semantic;

import java.util.List;
import java.util.Objects;

public record AnnotationValue(SemanticType type, Content value) {
  public AnnotationValue {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
  }

  public sealed interface Content
      permits Literal, Null, ListValue, AnnotationDeclarationReference {}

  public record Literal(Object value) implements Content {
    public Literal {
      Objects.requireNonNull(value, "value");
      if (!(value instanceof Boolean)
          && !(value instanceof Integer)
          && !(value instanceof java.math.BigInteger)
          && !(value instanceof java.math.BigDecimal)
          && !(value instanceof String)) {
        throw new IllegalArgumentException("unsupported annotation literal");
      }
    }
  }

  public enum Null implements Content {
    INSTANCE
  }

  public record ListValue(List<AnnotationValue> values) implements Content {
    public ListValue {
      values = List.copyOf(values);
    }
  }
}
