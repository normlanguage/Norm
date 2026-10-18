package dev.w0fv1.norm.execution;

import java.util.List;

public record LiveExecutionStatus(long revision, List<String> pendingLocations) {
  public LiveExecutionStatus {
    if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
    pendingLocations = pendingLocations.stream().distinct().sorted().toList();
  }
}
