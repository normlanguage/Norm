package dev.w0fv1.norm.project;

import java.util.List;

public record ProjectTestReport(
    long testsFound,
    long testsSucceeded,
    long testsFailed,
    long testsSkipped,
    long containersFailed,
    List<ProjectTestFailure> failures) {
  public ProjectTestReport {
    if (testsFound < 0
        || testsSucceeded < 0
        || testsFailed < 0
        || testsSkipped < 0
        || containersFailed < 0) {
      throw new IllegalArgumentException("test counts must not be negative");
    }
    failures = List.copyOf(failures);
  }

  public boolean isSuccess() {
    return testsFailed == 0 && containersFailed == 0 && failures.isEmpty();
  }
}
