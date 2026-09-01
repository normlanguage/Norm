package dev.w0fv1.norm.jvm;

public enum JavaPrimitiveType implements JavaBindingType {
  BOOLEAN("Z", "boolean"),
  BYTE("B", "byte"),
  SHORT("S", "short"),
  INT("I", "int"),
  LONG("J", "long"),
  FLOAT("F", "float"),
  DOUBLE("D", "double"),
  CHAR("C", "char"),
  VOID("V", "void");

  private final String descriptor;
  private final String displayName;

  JavaPrimitiveType(String descriptor, String displayName) {
    this.descriptor = descriptor;
    this.displayName = displayName;
  }

  @Override
  public String descriptor() {
    return descriptor;
  }

  @Override
  public String displayName() {
    return displayName;
  }
}
