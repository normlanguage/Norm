package dev.w0fv1.norm.value;

import java.util.Arrays;
import java.util.Optional;

public enum AnnotationRetention {
  SOURCE("source"),
  BINARY("binary"),
  RUNTIME("runtime");

  private final String keyword;

  AnnotationRetention(String keyword) {
    this.keyword = keyword;
  }

  public String keyword() {
    return keyword;
  }

  public static Optional<AnnotationRetention> fromKeyword(String keyword) {
    return Arrays.stream(values()).filter(value -> value.keyword.equals(keyword)).findFirst();
  }
}
