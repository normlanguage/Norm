package dev.w0fv1.norm.core;

public enum LanguageSemanticsVersion {
  V1(1);

  private final int code;

  LanguageSemanticsVersion(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
