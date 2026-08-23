package dev.w0fv1.norm.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record SemanticType(
    Kind kind,
    String identity,
    String name,
    List<SemanticType> arguments,
    ValueCategory category,
    Nullability nullability) {
  public static final SemanticType DYNAMIC =
      new SemanticType(Kind.ERROR, "<error>", "value", List.of(), ValueCategory.DYNAMIC);
  public static final SemanticType INTEGER =
      new SemanticType(
          Kind.DECLARED, "std.core.Integer", "Integer", List.of(), ValueCategory.VALUE);
  public static final SemanticType CODE_POINT =
      new SemanticType(
          Kind.DECLARED, "std.core.CodePoint", "CodePoint", List.of(), ValueCategory.VALUE);
  public static final SemanticType BOOLEAN =
      new SemanticType(
          Kind.DECLARED, "std.core.Boolean", "Boolean", List.of(), ValueCategory.VALUE);
  public static final SemanticType STRING =
      new SemanticType(Kind.DECLARED, "std.core.String", "String", List.of(), ValueCategory.VALUE);
  public static final SemanticType VOID =
      new SemanticType(Kind.VOID, "std.core.Void", "Void", List.of(), ValueCategory.VOID);
  public static final SemanticType NULL =
      new SemanticType(Kind.NULL, "<null>", "null", List.of(), ValueCategory.VALUE);

  public SemanticType(
      Kind kind,
      String identity,
      String name,
      List<SemanticType> arguments,
      ValueCategory category) {
    this(kind, identity, name, arguments, category, Nullability.NON_NULL);
  }

  public SemanticType(String name) {
    this(
        name.equals("value") ? Kind.ERROR : name.equals("Void") ? Kind.VOID : Kind.DECLARED,
        name.equals("value") ? "<error>" : "std.core." + name,
        name,
        List.of(),
        switch (name) {
          case "value" -> ValueCategory.DYNAMIC;
          case "Void" -> ValueCategory.VOID;
          default -> ValueCategory.VALUE;
        },
        Nullability.NON_NULL);
  }

  public SemanticType(String name, ValueCategory category) {
    this(Kind.DECLARED, name, name, List.of(), category, Nullability.NON_NULL);
  }

  public SemanticType {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(name, "name");
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(nullability, "nullability");
    if (identity.isBlank() || name.isBlank()) {
      throw new IllegalArgumentException("type identity and name must not be blank");
    }
    if (kind == Kind.TYPE_PARAMETER && !arguments.isEmpty()) {
      throw new IllegalArgumentException("type parameters cannot have arguments");
    }
    if ((kind == Kind.VOID || kind == Kind.NULL || kind == Kind.ERROR)
        && nullability == Nullability.NULLABLE) {
      throw new IllegalArgumentException(kind + " cannot be nullable");
    }
  }

  public static SemanticType declared(
      String identity, String name, List<SemanticType> arguments, ValueCategory category) {
    return new SemanticType(
        Kind.DECLARED, identity, name, arguments, category, Nullability.NON_NULL);
  }

  public static SemanticType parameter(String identity, String name) {
    return new SemanticType(
        Kind.TYPE_PARAMETER, identity, name, List.of(), ValueCategory.VALUE, Nullability.NON_NULL);
  }

  public String displayName() {
    String base =
        arguments.isEmpty()
            ? name
            : name
                + "<"
                + arguments.stream()
                    .map(SemanticType::displayName)
                    .collect(Collectors.joining(", "))
                + ">";
    return nullability == Nullability.NULLABLE ? base + "?" : base;
  }

  public SemanticType substitute(Map<String, SemanticType> substitutions) {
    if (kind == Kind.TYPE_PARAMETER) {
      SemanticType substituted = substitutions.getOrDefault(identity, this);
      return nullability == Nullability.NULLABLE ? substituted.nullable() : substituted;
    }
    if (arguments.isEmpty()) return this;
    List<SemanticType> substituted =
        arguments.stream().map(argument -> argument.substitute(substitutions)).toList();
    return substituted.equals(arguments)
        ? this
        : new SemanticType(kind, identity, name, substituted, category, nullability);
  }

  public boolean isNullable() {
    return nullability == Nullability.NULLABLE;
  }

  public boolean mayContainNull() {
    return isNullable() || kind == Kind.NULL || kind == Kind.TYPE_PARAMETER;
  }

  public SemanticType nullable() {
    if (isNullable() || kind == Kind.ERROR) return this;
    return new SemanticType(kind, identity, name, arguments, category, Nullability.NULLABLE);
  }

  public SemanticType nonNullable() {
    if (!isNullable()) return this;
    return new SemanticType(kind, identity, name, arguments, category, Nullability.NON_NULL);
  }

  public enum Kind {
    DECLARED,
    TYPE_PARAMETER,
    NULL,
    VOID,
    ERROR
  }

  public enum Nullability {
    NON_NULL,
    NULLABLE
  }
}
