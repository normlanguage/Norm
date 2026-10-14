package dev.w0fv1.norm.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class CoreMetadata {
  private final List<CoreAnnotationApplication> annotations;

  public CoreMetadata(List<CoreAnnotationApplication> annotations) {
    List<CoreAnnotationApplication> values = List.copyOf(annotations);
    if (values.stream().anyMatch(Objects::isNull)) {
      throw new NullPointerException("annotation application");
    }
    if (values.stream().map(CoreAnnotationApplication::id).distinct().count() != values.size())
      throw new IllegalArgumentException("annotation authoring application ids must be unique");
    this.annotations =
        values.stream()
            .sorted(
                (left, right) -> {
                  int order =
                      Arrays.compareUnsigned(
                          CoreCodec.encodeAnnotationApplication(left),
                          CoreCodec.encodeAnnotationApplication(right));
                  return order == 0 ? left.id().compareTo(right.id()) : order;
                })
            .toList();
  }

  public static CoreMetadata empty() {
    return new CoreMetadata(List.of());
  }

  public List<CoreAnnotationApplication> annotations() {
    return annotations;
  }
}
