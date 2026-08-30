package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreCatchClause(CoreType type, int localIndex, CoreBlock body) {
  public CoreCatchClause {
    Objects.requireNonNull(type, "type");
    if (localIndex < 0) throw new IllegalArgumentException("local index must not be negative");
    Objects.requireNonNull(body, "body");
  }
}
