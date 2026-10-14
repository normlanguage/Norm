package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.util.Objects;

public record CoreAnnotationApplicationId(ModuleSourceCoordinate source, int ordinal)
    implements Comparable<CoreAnnotationApplicationId> {
  public CoreAnnotationApplicationId {
    Objects.requireNonNull(source, "source");
    if (ordinal < 0)
      throw new IllegalArgumentException("annotation application ordinal must not be negative");
  }

  @Override
  public int compareTo(CoreAnnotationApplicationId other) {
    int order = source.compareTo(other.source());
    return order == 0 ? Integer.compare(ordinal, other.ordinal()) : order;
  }
}
