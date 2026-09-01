package dev.w0fv1.norm.jvm;

import java.util.List;

public record JavaAnnotationArrayValue(List<JavaAnnotationValue> values)
    implements JavaAnnotationValue {
  public JavaAnnotationArrayValue {
    values = List.copyOf(values);
  }
}
