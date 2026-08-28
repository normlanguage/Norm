package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public record CoreAnnotationValue(CoreType type, Content value) {
  public CoreAnnotationValue {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
  }

  public sealed interface Content permits Literal, Null, ListValue, CoreAnnotationReference {}

  public record Literal(Object value) implements Content {
    public Literal {
      Objects.requireNonNull(value, "value");
      if (!(value instanceof Boolean)
          && !(value instanceof Integer)
          && !(value instanceof Long)
          && !(value instanceof Float)
          && !(value instanceof Double)
          && !(value instanceof String)) {
        throw new IllegalArgumentException("unsupported core annotation literal");
      }
    }
  }

  public enum Null implements Content {
    INSTANCE
  }

  public record ListValue(List<CoreAnnotationValue> values) implements Content {
    public ListValue {
      values = List.copyOf(values);
    }
  }
}
