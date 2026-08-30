package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreSwitchCase(CorePattern pattern, CoreBlock body) {
  public CoreSwitchCase {
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(body, "body");
  }
}
