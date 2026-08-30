package dev.w0fv1.norm.core.store;

import java.io.IOException;
import java.io.Serial;

public final class DefinitionStorePolicyMismatchException extends IOException {
  @Serial private static final long serialVersionUID = 1L;

  private final int configuredMaximumGroups;
  private final long configuredMaximumBytes;
  private final int requestedMaximumGroups;
  private final long requestedMaximumBytes;

  public DefinitionStorePolicyMismatchException(
      int configuredMaximumGroups,
      long configuredMaximumBytes,
      int requestedMaximumGroups,
      long requestedMaximumBytes) {
    super(
        "definition store policy mismatch: configured maximum is "
            + configuredMaximumGroups
            + " groups and "
            + configuredMaximumBytes
            + " bytes, requested maximum is "
            + requestedMaximumGroups
            + " groups and "
            + requestedMaximumBytes
            + " bytes");
    this.configuredMaximumGroups = configuredMaximumGroups;
    this.configuredMaximumBytes = configuredMaximumBytes;
    this.requestedMaximumGroups = requestedMaximumGroups;
    this.requestedMaximumBytes = requestedMaximumBytes;
  }

  public int configuredMaximumGroups() {
    return configuredMaximumGroups;
  }

  public long configuredMaximumBytes() {
    return configuredMaximumBytes;
  }

  public int requestedMaximumGroups() {
    return requestedMaximumGroups;
  }

  public long requestedMaximumBytes() {
    return requestedMaximumBytes;
  }
}
