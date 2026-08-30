package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.core.DefinitionGroupId;
import java.io.IOException;
import java.io.Serial;
import java.util.Objects;

public final class CorruptDefinitionException extends IOException {
  @Serial private static final long serialVersionUID = 1L;

  private final String expected;
  private final String actual;

  public CorruptDefinitionException(DefinitionGroupId expected, DefinitionGroupId actual) {
    super(
        "definition content hash does not match its key: expected "
            + expected
            + ", actual "
            + actual);
    this.expected = Objects.requireNonNull(expected, "expected").toString();
    this.actual = Objects.requireNonNull(actual, "actual").toString();
  }

  public DefinitionGroupId expected() {
    return DefinitionGroupId.parse(expected);
  }

  public DefinitionGroupId actual() {
    return DefinitionGroupId.parse(actual);
  }
}
