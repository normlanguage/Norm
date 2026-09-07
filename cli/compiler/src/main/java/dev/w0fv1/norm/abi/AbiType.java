package dev.w0fv1.norm.abi;

import java.util.List;
import java.util.Objects;

public record AbiType(
    Kind kind,
    String identity,
    String name,
    List<AbiType> arguments,
    Category category,
    boolean nullable) {
  public enum Kind {
    DECLARED,
    TYPE_PARAMETER,
    REFERENCE,
    VOID,
    NULL,
    ERROR,
    EXISTENTIAL
  }

  public enum Category {
    VALUE,
    IDENTITY,
    POLYMORPHIC,
    DYNAMIC,
    VOID
  }

  public AbiType {
    Objects.requireNonNull(kind);
    Objects.requireNonNull(identity);
    Objects.requireNonNull(name);
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(category);
    if (identity.isBlank() || name.isBlank())
      throw new IllegalArgumentException("ABI type identity and name must not be blank");
    if (kind == Kind.REFERENCE && arguments.size() != 1)
      throw new IllegalArgumentException("ABI reference requires one target");
    if ((kind == Kind.TYPE_PARAMETER || kind == Kind.EXISTENTIAL) && !arguments.isEmpty())
      throw new IllegalArgumentException("ABI type variable cannot have arguments");
    if (nullable && kind != Kind.DECLARED && kind != Kind.TYPE_PARAMETER)
      throw new IllegalArgumentException("ABI type cannot be nullable: " + kind);
  }

  public static AbiType declared(
      String identity, String name, List<AbiType> arguments, Category category) {
    return new AbiType(Kind.DECLARED, identity, name, arguments, category, false);
  }

  public boolean isNullable() {
    return nullable;
  }

  public boolean isReference() {
    return kind == Kind.REFERENCE;
  }

  public AbiType referenceTarget() {
    if (!isReference()) throw new IllegalStateException("ABI type is not a reference");
    return arguments.getFirst();
  }

  public boolean isFunction() {
    return kind == Kind.DECLARED && identity.equals("std.core.Function") && !arguments.isEmpty();
  }

  public boolean isUnknownFunction() {
    return isFunction() && arguments.size() == 1 && arguments.getFirst().kind() == Kind.EXISTENTIAL;
  }

  public AbiType functionReturnType() {
    if (!isFunction() || isUnknownFunction())
      throw new IllegalStateException("ABI function signature is not known");
    return arguments.getFirst();
  }

  public List<AbiType> functionParameterTypes() {
    if (!isFunction() || isUnknownFunction())
      throw new IllegalStateException("ABI function signature is not known");
    return arguments.subList(1, arguments.size());
  }
}
