package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.List;
import java.util.Objects;

public record BoundAnnotationValue(SemanticType type, Content value) {
  public BoundAnnotationValue {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
  }

  public sealed interface Content permits Literal, Null, ListValue, BoundAnnotationReference {}

  public record Literal(Object value) implements Content {
    public Literal {
      Objects.requireNonNull(value, "value");
    }
  }

  public enum Null implements Content {
    INSTANCE
  }

  public record ListValue(List<BoundAnnotationValue> values) implements Content {
    public ListValue {
      values = List.copyOf(values);
    }
  }
}
