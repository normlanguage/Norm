package dev.w0fv1.norm.value;

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
}
