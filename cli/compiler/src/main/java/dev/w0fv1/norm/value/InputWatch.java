package dev.w0fv1.norm.value;

import java.nio.file.Path;
import java.util.Objects;

public record InputWatch(Path root, String pattern) {
  public InputWatch {
    root = root.toAbsolutePath().normalize();
    Objects.requireNonNull(pattern, "pattern");
  }

  public static InputWatch file(Path path) {
    Path file = path.toAbsolutePath().normalize();
    return new InputWatch(file.getParent(), literal(file.getFileName().toString()));
  }

  public String absolutePattern() {
    return literal(root.toString().replace('\\', '/')) + "/" + pattern;
  }

  private static String literal(String path) {
    var result = new StringBuilder();
    for (int index = 0; index < path.length(); index++) {
      char character = path.charAt(index);
      if ("[]{}*?".indexOf(character) >= 0) result.append('[').append(character).append(']');
      else result.append(character);
    }
    return result.toString();
  }
}
