package dev.w0fv1.norm.core;

public enum CoreSchemaVersion {
  V1(1),
  V2(2);

  private final int code;

  CoreSchemaVersion(int code) {
    this.code = code;
  }

  public int code() {
    return code;
  }
}
