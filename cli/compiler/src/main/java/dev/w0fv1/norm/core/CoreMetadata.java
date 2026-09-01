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
    this.annotations =
        values.stream()
            .sorted(
                (left, right) ->
                    Arrays.compareUnsigned(
                        CoreCodec.encodeAnnotationApplication(left),
                        CoreCodec.encodeAnnotationApplication(right)))
            .toList();
  }

  public static CoreMetadata empty() {
    return new CoreMetadata(List.of());
  }

  public List<CoreAnnotationApplication> annotations() {
    return annotations;
  }
}
