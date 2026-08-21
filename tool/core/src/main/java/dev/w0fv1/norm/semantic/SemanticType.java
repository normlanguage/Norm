package dev.w0fv1.norm.semantic;

import java.util.Objects;

public record SemanticType(String displayName, ValueCategory category) {
  public static final SemanticType DYNAMIC = new SemanticType("value", ValueCategory.DYNAMIC);

  public SemanticType(String displayName) {
    this(
        displayName,
        switch (displayName) {
          case "value" -> ValueCategory.DYNAMIC;
          case "void" -> ValueCategory.VOID;
          default -> ValueCategory.VALUE;
        });
  }

  public SemanticType {
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(category, "category");
    if (displayName.isBlank()) throw new IllegalArgumentException("type name must not be blank");
  }
}
