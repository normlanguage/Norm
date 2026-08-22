package dev.w0fv1.norm.semantic;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record SemanticType(
    Kind kind, String identity, String name, List<SemanticType> arguments, ValueCategory category) {
  public static final SemanticType DYNAMIC =
      new SemanticType(Kind.ERROR, "<error>", "value", List.of(), ValueCategory.DYNAMIC);
  public static final SemanticType VOID =
      new SemanticType(Kind.VOID, "std.core.void", "void", List.of(), ValueCategory.VOID);

  public SemanticType(String name) {
    this(
        name.equals("value") ? Kind.ERROR : name.equals("void") ? Kind.VOID : Kind.DECLARED,
        name.equals("value") ? "<error>" : "std.core." + name,
        name,
        List.of(),
        switch (name) {
          case "value" -> ValueCategory.DYNAMIC;
          case "void" -> ValueCategory.VOID;
          default -> ValueCategory.VALUE;
        });
  }

  public SemanticType(String name, ValueCategory category) {
    this(Kind.DECLARED, name, name, List.of(), category);
  }

  public SemanticType {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(identity, "identity");
    Objects.requireNonNull(name, "name");
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(category, "category");
    if (identity.isBlank() || name.isBlank()) {
      throw new IllegalArgumentException("type identity and name must not be blank");
    }
    if (kind == Kind.TYPE_PARAMETER && !arguments.isEmpty()) {
      throw new IllegalArgumentException("type parameters cannot have arguments");
    }
  }

  public static SemanticType declared(
      String identity, String name, List<SemanticType> arguments, ValueCategory category) {
    return new SemanticType(Kind.DECLARED, identity, name, arguments, category);
  }

  public static SemanticType parameter(String identity, String name) {
    return new SemanticType(Kind.TYPE_PARAMETER, identity, name, List.of(), ValueCategory.VALUE);
  }

  public String displayName() {
    if (arguments.isEmpty()) return name;
    return name
        + "<"
        + arguments.stream().map(SemanticType::displayName).collect(Collectors.joining(", "))
        + ">";
  }

  public SemanticType substitute(Map<String, SemanticType> substitutions) {
    if (kind == Kind.TYPE_PARAMETER) return substitutions.getOrDefault(identity, this);
    if (arguments.isEmpty()) return this;
    List<SemanticType> substituted =
        arguments.stream().map(argument -> argument.substitute(substitutions)).toList();
    return substituted.equals(arguments)
        ? this
        : new SemanticType(kind, identity, name, substituted, category);
  }

  public enum Kind {
    DECLARED,
    TYPE_PARAMETER,
    VOID,
    ERROR
  }
}
