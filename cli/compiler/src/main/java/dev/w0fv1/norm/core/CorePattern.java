package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public sealed interface CorePattern
    permits CorePattern.Variant,
        CorePattern.Binding,
        CorePattern.Wildcard,
        CorePattern.Literal,
        CorePattern.Null {
  record Variant(String variantKey, List<CorePattern> arguments) implements CorePattern {
    public Variant {
      Objects.requireNonNull(variantKey, "variantKey");
      if (variantKey.isBlank()) throw new IllegalArgumentException("variant key must not be blank");
      arguments = List.copyOf(arguments);
    }
  }

  record Binding(int localIndex, CoreType type) implements CorePattern {
    public Binding {
      if (localIndex < 0) throw new IllegalArgumentException("local index must not be negative");
      Objects.requireNonNull(type, "type");
    }
  }

  enum Wildcard implements CorePattern {
    INSTANCE
  }

  record Literal(Object value, CoreType type) implements CorePattern {
    public Literal {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(type, "type");
      if (!(value instanceof Long
          || value instanceof Integer
          || value instanceof Float
          || value instanceof Double
          || value instanceof Boolean
          || value instanceof String)) {
        throw new IllegalArgumentException("unsupported core pattern literal");
      }
    }
  }

  enum Null implements CorePattern {
    INSTANCE
  }
}
