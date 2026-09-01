package dev.w0fv1.norm.jvm;

public enum JavaCallableKind {
  ARRAY_CONSTRUCTOR,
  ARRAY_LENGTH,
  ARRAY_GET,
  ARRAY_SET,
  CONSTRUCTOR,
  STATIC_METHOD,
  INSTANCE_METHOD,
  STATIC_FIELD_GET,
  STATIC_FIELD_SET,
  INSTANCE_FIELD_GET,
  INSTANCE_FIELD_SET;

  public boolean requiresReceiver() {
    return this == ARRAY_LENGTH
        || this == ARRAY_GET
        || this == ARRAY_SET
        || this == INSTANCE_METHOD
        || this == INSTANCE_FIELD_GET
        || this == INSTANCE_FIELD_SET;
  }

  public boolean isField() {
    return this == STATIC_FIELD_GET
        || this == STATIC_FIELD_SET
        || this == INSTANCE_FIELD_GET
        || this == INSTANCE_FIELD_SET;
  }
}
