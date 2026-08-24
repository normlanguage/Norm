package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.Objects;

public record BoundSwitchCase(BoundPattern pattern, BoundBlock body, SourceSpan span)
    implements BoundNode {
  public BoundSwitchCase {
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(span, "span");
  }
}
