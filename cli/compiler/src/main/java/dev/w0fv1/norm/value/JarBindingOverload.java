package dev.w0fv1.norm.value;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record JarBindingOverload(String name, List<String> parameterTypes) {
  private static final Set<String> PRIMITIVE_TYPES =
      Set.of("boolean", "byte", "short", "char", "int", "long", "float", "double");

  public JarBindingOverload {
    Objects.requireNonNull(name, "name");
    parameterTypes = List.copyOf(parameterTypes);
    if (!name.equals("new")
        && (name.isEmpty()
            || !Character.isJavaIdentifierStart(name.charAt(0))
            || name.chars().skip(1).anyMatch(value -> !Character.isJavaIdentifierPart(value)))) {
      throw new IllegalArgumentException("invalid JAR binding overload name '" + name + "'");
    }
    for (String parameterType : parameterTypes) {
      Objects.requireNonNull(parameterType, "parameterType");
      if (!validTypeName(parameterType)) {
        throw new IllegalArgumentException(
            "invalid JAR binding overload parameter type '" + parameterType + "'");
      }
    }
  }

  private static boolean validTypeName(String value) {
    if (value.isEmpty() || !value.equals(value.strip())) return false;
    String component = value;
    while (component.endsWith("[]")) {
      component = component.substring(0, component.length() - 2);
    }
    if (component.isEmpty()) return false;
    if (PRIMITIVE_TYPES.contains(component)) {
      return true;
    }
    String[] segments = component.replace('$', '.').split("\\.", -1);
    for (String segment : segments) {
      if (segment.isEmpty() || !Character.isJavaIdentifierStart(segment.charAt(0))) return false;
      if (segment
          .chars()
          .skip(1)
          .anyMatch(codePoint -> !Character.isJavaIdentifierPart(codePoint))) {
        return false;
      }
    }
    return true;
  }
}
