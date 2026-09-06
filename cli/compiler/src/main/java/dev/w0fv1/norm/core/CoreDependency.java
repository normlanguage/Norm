package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreDependency(Kind kind, CoreDefinitionLink target) {
  public CoreDependency {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(target, "target");
  }

  public enum Kind {
    TYPE,
    DECLARED_MEMBER,
    IMPLEMENTATION,
    ANNOTATION,
    ANNOTATION_CALLABLE,
    FIELD,
    CONSTRUCTION,
    CALL,
    VIRTUAL_CALL,
    CLOSURE,
    INTERFACE_CALL
  }
}
