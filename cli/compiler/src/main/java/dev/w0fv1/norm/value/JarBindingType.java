package dev.w0fv1.norm.value;

import dev.w0fv1.norm.syntax.LanguageSyntax;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record JarBindingType(
    String name, List<String> members, List<JarBindingOverload> overloads) {
  public JarBindingType(String name, List<String> members) {
    this(name, members, List.of());
  }

  public JarBindingType {
    Objects.requireNonNull(name, "name");
    members = List.copyOf(members);
    overloads = List.copyOf(overloads);
    if (name.isBlank())
      throw new IllegalArgumentException("JAR binding type name must not be blank");
    for (String segment : name.split("\\.", -1)) {
      if (!LanguageSyntax.isIdentifier(segment)) {
        throw new IllegalArgumentException("invalid JAR binding type name '" + name + "'");
      }
    }
    HashSet<String> unique = new HashSet<>();
    for (String member : members) {
      Objects.requireNonNull(member, "member");
      if (!member.equals("new")
          && (member.isEmpty()
              || !Character.isJavaIdentifierStart(member.charAt(0))
              || member
                  .chars()
                  .skip(1)
                  .anyMatch(value -> !Character.isJavaIdentifierPart(value)))) {
        throw new IllegalArgumentException(
            "invalid JAR binding member name '" + name + "." + member + "'");
      }
      if (!unique.add(member)) {
        throw new IllegalArgumentException(
            "duplicate JAR binding member '" + name + "." + member + "'");
      }
    }
    HashSet<JarBindingOverload> uniqueOverloads = new HashSet<>();
    for (JarBindingOverload overload : overloads) {
      Objects.requireNonNull(overload, "overload");
      if (unique.contains(overload.name())) {
        throw new IllegalArgumentException(
            "JAR binding member group and overload overlap: '"
                + name
                + "."
                + overload.name()
                + "'");
      }
      if (!uniqueOverloads.add(overload)) {
        throw new IllegalArgumentException(
            "duplicate JAR binding overload '"
                + name
                + "."
                + overload.name()
                + overload.parameterTypes()
                + "'");
      }
    }
  }
}
