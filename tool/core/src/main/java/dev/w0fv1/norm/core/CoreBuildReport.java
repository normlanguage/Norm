package dev.w0fv1.norm.core;

public record CoreBuildReport(
    int definitions,
    int groups,
    int storedGroups,
    int reusedGroups,
    int notAdmittedGroups,
    CoreCanonicalizationMetrics canonicalization) {
  public CoreBuildReport {
    if (definitions < 0
        || groups < 0
        || storedGroups < 0
        || reusedGroups < 0
        || notAdmittedGroups < 0) {
      throw new IllegalArgumentException("core build counts must not be negative");
    }
    if ((long) storedGroups + reusedGroups + notAdmittedGroups != groups) {
      throw new IllegalArgumentException("store outcomes must cover the build groups");
    }
    java.util.Objects.requireNonNull(canonicalization, "canonicalization");
  }
}
