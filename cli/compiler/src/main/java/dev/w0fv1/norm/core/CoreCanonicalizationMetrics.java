package dev.w0fv1.norm.core;

public record CoreCanonicalizationMetrics(
    int components,
    int maximumComponentSize,
    long refinementRounds,
    long searchBranches,
    long memoizedSearches,
    long automorphicBranches) {}
