package dev.w0fv1.norm.value;

import java.util.Arrays;
import java.util.Optional;

public enum AnnotationTarget {
  PACKAGE("package"),
  TYPE("type"),
  FIELD("field"),
  CONSTRUCTOR("constructor"),
  FUNCTION("function"),
  PARAMETER("parameter"),
  LOCAL("local");

  private final String keyword;

  AnnotationTarget(String keyword) {
    this.keyword = keyword;
  }

  public String keyword() {
    return keyword;
  }

  public static Optional<AnnotationTarget> fromKeyword(String keyword) {
    return Arrays.stream(values()).filter(value -> value.keyword.equals(keyword)).findFirst();
  }
}
