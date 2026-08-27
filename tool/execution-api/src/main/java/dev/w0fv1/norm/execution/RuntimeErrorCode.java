package dev.w0fv1.norm.execution;

public enum RuntimeErrorCode {
  INDEX_OUT_OF_BOUNDS("NORM-RUNTIME-0001"),
  MISSING_MAP_KEY("NORM-RUNTIME-0002"),
  EMPTY_COLLECTION("NORM-RUNTIME-0003"),
  DIVISION_BY_ZERO("NORM-RUNTIME-0004"),
  CANCELLED("NORM-RUNTIME-0005"),
  INVALID_ARGUMENT("NORM-RUNTIME-0006"),
  UNCAUGHT_EXCEPTION("NORM-RUNTIME-0007");

  private final String id;

  RuntimeErrorCode(String id) {
    this.id = id;
  }

  public String id() {
    return id;
  }
}
