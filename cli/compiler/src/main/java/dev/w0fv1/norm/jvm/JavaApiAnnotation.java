package dev.w0fv1.norm.jvm;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record JavaApiAnnotation(
    String type, boolean runtimeVisible, List<JavaAnnotationElement> elements) {
  public JavaApiAnnotation {
    Objects.requireNonNull(type, "type");
    elements = elements.stream().sorted(Comparator.comparing(JavaAnnotationElement::name)).toList();
  }
}
