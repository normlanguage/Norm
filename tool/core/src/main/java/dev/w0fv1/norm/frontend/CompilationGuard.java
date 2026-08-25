package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.CompilationRequest;

final class CompilationGuard {
  private final CancellationToken cancellation;
  private final CompilationLimits limits;
  private long workUnits;

  CompilationGuard(CancellationToken cancellation, CompilationLimits limits) {
    this.cancellation = cancellation;
    this.limits = limits;
  }

  static CompilationGuard unlimited() {
    return new CompilationGuard(
        CancellationToken.none(),
        new CompilationLimits(Integer.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
  }

  void validate(CompilationRequest request) {
    checkpoint();
    if (request.sources().size() > limits.maximumDocuments()) {
      throw new CompilationBudgetExceededException("document", limits.maximumDocuments());
    }
    long characters = 0;
    for (var source : request.sources()) {
      characters = Math.addExact(characters, source.length());
      if (characters > limits.maximumSourceCharacters()) {
        throw new CompilationBudgetExceededException(
            "source character", limits.maximumSourceCharacters());
      }
    }
  }

  void checkpoint() {
    if (cancellation.isCancellationRequested()) throw new CompilationCancelledException();
    workUnits++;
    if (workUnits > limits.maximumWorkUnits()) {
      throw new CompilationBudgetExceededException("work unit", limits.maximumWorkUnits());
    }
  }

  boolean isCancellationRequested() {
    return cancellation.isCancellationRequested();
  }

  long maximumCanonicalSearchBranches() {
    return limits.maximumCanonicalSearchBranches();
  }
}
