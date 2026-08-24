package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.core.DefinitionGroupId;
import java.util.Objects;

public record PutResult(DefinitionGroupId id, Status status) {
  public PutResult {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(status, "status");
  }

  public enum Status {
    STORED,
    REUSED,
    NOT_ADMITTED
  }
}
