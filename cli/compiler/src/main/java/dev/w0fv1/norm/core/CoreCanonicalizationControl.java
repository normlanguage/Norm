package dev.w0fv1.norm.core;

import java.util.function.BooleanSupplier;

public record CoreCanonicalizationControl(
    BooleanSupplier cancellation, long maximumSearchBranches) {
  private static final CoreCanonicalizationControl STANDARD =
      new CoreCanonicalizationControl(() -> false, 1_000_000);

  public CoreCanonicalizationControl {
    java.util.Objects.requireNonNull(cancellation, "cancellation");
    if (maximumSearchBranches < 1) {
      throw new IllegalArgumentException("maximumSearchBranches must be positive");
    }
  }

  public static CoreCanonicalizationControl standard() {
    return STANDARD;
  }

  State begin() {
    return new State(cancellation, maximumSearchBranches);
  }

  static final class State {
    private final BooleanSupplier cancellation;
    private final long maximumSearchBranches;
    private long searchBranches;
    private long refinementRounds;
    private long memoizedSearches;
    private long automorphicBranches;

    private State(BooleanSupplier cancellation, long maximumSearchBranches) {
      this.cancellation = cancellation;
      this.maximumSearchBranches = maximumSearchBranches;
    }

    void checkpoint() {
      if (cancellation.getAsBoolean()) throw new CoreCanonicalizationCancelledException();
    }

    void searchBranch() {
      checkpoint();
      searchBranches++;
      if (searchBranches > maximumSearchBranches) {
        throw new CoreCanonicalizationBudgetExceededException(maximumSearchBranches);
      }
    }

    void refinementRound() {
      checkpoint();
      refinementRounds++;
    }

    void memoizedSearch() {
      memoizedSearches++;
    }

    void automorphicBranch() {
      automorphicBranches++;
    }

    CoreCanonicalizationMetrics metrics(int components, int maximumComponentSize) {
      return new CoreCanonicalizationMetrics(
          components,
          maximumComponentSize,
          refinementRounds,
          searchBranches,
          memoizedSearches,
          automorphicBranches);
    }
  }
}
