package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreLocal(int index, CoreType type, Kind kind) {
  public CoreLocal {
    if (index < 0) throw new IllegalArgumentException("local index must not be negative");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(kind, "kind");
  }

  public enum Kind {
    RECEIVER,
    CAPTURE,
    PARAMETER,
    REIFIED_TYPE,
    VARIABLE,
    ITERATOR
  }
}
