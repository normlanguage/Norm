package dev.w0fv1.norm.core;

import java.util.Objects;
import java.util.Optional;

public record CoreBinding(
    String packageName,
    Optional<String> ownerName,
    String name,
    CoreVisibility visibility,
    CoreBindingShape shape,
    DefinitionOccurrenceId occurrence,
    boolean exported) {
  public CoreBinding {
    Objects.requireNonNull(packageName, "packageName");
    ownerName = Objects.requireNonNull(ownerName, "ownerName");
    Objects.requireNonNull(name, "name");
    if (name.isBlank()) throw new IllegalArgumentException("core binding name must not be blank");
    Objects.requireNonNull(visibility, "visibility");
    Objects.requireNonNull(shape, "shape");
    Objects.requireNonNull(occurrence, "occurrence");
    if (!(shape instanceof CoreBindingShape.Callable
            || shape instanceof CoreBindingShape.InterfaceMethod)
        && ownerName.isPresent()) {
      throw new IllegalArgumentException("only method bindings may have an owner");
    }
  }

  public CoreBindingKind kind() {
    return switch (shape) {
      case CoreBindingShape.Callable ignored ->
          ownerName.isPresent() ? CoreBindingKind.METHOD : CoreBindingKind.FUNCTION;
      case CoreBindingShape.Class ignored -> CoreBindingKind.CLASS;
      case CoreBindingShape.Enum ignored -> CoreBindingKind.ENUM;
      case CoreBindingShape.Interface ignored -> CoreBindingKind.INTERFACE;
      case CoreBindingShape.InterfaceMethod ignored -> CoreBindingKind.INTERFACE_METHOD;
    };
  }

  public DefinitionId definition() {
    return occurrence.representative();
  }
}
