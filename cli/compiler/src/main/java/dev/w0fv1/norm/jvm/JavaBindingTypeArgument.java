package dev.w0fv1.norm.jvm;

import java.util.Objects;
import java.util.Optional;

public record JavaBindingTypeArgument(JavaTypeVariance variance, Optional<JavaBindingType> type) {
  public JavaBindingTypeArgument {
    Objects.requireNonNull(variance, "variance");
    Objects.requireNonNull(type, "type");
    if ((variance == JavaTypeVariance.UNBOUNDED) != type.isEmpty()) {
      throw new IllegalArgumentException("only an unbounded type argument omits its type");
    }
  }

  public static JavaBindingTypeArgument unbounded() {
    return new JavaBindingTypeArgument(JavaTypeVariance.UNBOUNDED, Optional.empty());
  }

  public static JavaBindingTypeArgument exact(JavaBindingType type) {
    return new JavaBindingTypeArgument(JavaTypeVariance.EXACT, Optional.of(type));
  }
}
