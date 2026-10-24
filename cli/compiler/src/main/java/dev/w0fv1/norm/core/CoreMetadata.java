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
            .map(
                annotation ->
                    new EncodedAnnotation(
                        annotation, CoreCodec.encodeAnnotationApplication(annotation)))
            .sorted(
                (left, right) -> {
                  int order = Arrays.compareUnsigned(left.bytes(), right.bytes());
                  return order == 0
                      ? left.annotation().id().compareTo(right.annotation().id())
                      : order;
                })
            .map(EncodedAnnotation::annotation)
            .toList();
  }

  private record EncodedAnnotation(CoreAnnotationApplication annotation, byte[] bytes) {}

  public static CoreMetadata empty() {
    return new CoreMetadata(List.of());
  }

  public List<CoreAnnotationApplication> annotations() {
    return annotations;
  }
}
