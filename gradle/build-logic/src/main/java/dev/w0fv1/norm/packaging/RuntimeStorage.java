package dev.w0fv1.norm.packaging;

public enum RuntimeStorage {
  SEALED,
  SYSTEM;

  public static RuntimeStorage parse(String value) {
    return switch (value) {
      case "sealed" -> SEALED;
      case "system" -> SYSTEM;
      default -> throw new IllegalArgumentException("Invalid runtime storage: " + value);
    };
  }
}
