package dev.w0fv1.norm.core;

public record CoreBuildReport(
    int definitions,
    int convertedDefinitions,
    int relinkedDefinitions,
    int importedDefinitions,
    int groups,
    CoreCanonicalizationMetrics canonicalization) {
  public CoreBuildReport {
    if (definitions < 0
        || convertedDefinitions < 0
        || relinkedDefinitions < 0
        || importedDefinitions < 0
        || (long) convertedDefinitions + relinkedDefinitions + importedDefinitions > definitions
        || groups < 0) throw new IllegalArgumentException("core build counts must not be negative");
    java.util.Objects.requireNonNull(canonicalization, "canonicalization");
  }

  public int reusedDefinitions() {
    return definitions - convertedDefinitions - relinkedDefinitions - importedDefinitions;
  }
}
