package dev.w0fv1.norm.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CoreAnnotationPath(List<Step> steps) implements Comparable<CoreAnnotationPath> {
  public static final CoreAnnotationPath ROOT = new CoreAnnotationPath(List.of());

  public CoreAnnotationPath {
    steps = List.copyOf(steps);
  }

  public CoreAnnotationPath child(Kind kind, int index) {
    var result = new ArrayList<>(steps);
    result.add(new Step(kind, index));
    return new CoreAnnotationPath(result);
  }

  @Override
  public int compareTo(CoreAnnotationPath other) {
    for (int index = 0; index < Math.min(steps.size(), other.steps.size()); index++) {
      int order = steps.get(index).compareTo(other.steps.get(index));
      if (order != 0) return order;
    }
    return Integer.compare(steps.size(), other.steps.size());
  }

  public enum Kind {
    PARAMETER,
    FIELD,
    VARIANT,
    INTERCEPTOR,
    VALUE,
    LIST_ITEM
  }

  public record Step(Kind kind, int index) implements Comparable<Step> {
    public Step {
      Objects.requireNonNull(kind, "kind");
      if (index < 0)
        throw new IllegalArgumentException("annotation path index must not be negative");
    }

    @Override
    public int compareTo(Step other) {
      int order = kind.compareTo(other.kind());
      return order == 0 ? Integer.compare(index, other.index()) : order;
    }
  }
}
