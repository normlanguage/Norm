package dev.w0fv1.norm.jvm;

import java.util.Objects;
import java.util.Optional;

public record JavaTypeArgument(JavaTypeVariance variance, Optional<JavaTypeSignature> type) {
  public JavaTypeArgument {
    Objects.requireNonNull(variance, "variance");
    Objects.requireNonNull(type, "type");
    if ((variance == JavaTypeVariance.UNBOUNDED) != type.isEmpty()) {
      throw new IllegalArgumentException("only an unbounded type argument omits its type");
    }
  }

  public static JavaTypeArgument unbounded() {
    return new JavaTypeArgument(JavaTypeVariance.UNBOUNDED, Optional.empty());
  }

  public static JavaTypeArgument of(JavaTypeVariance variance, JavaTypeSignature type) {
    return new JavaTypeArgument(variance, Optional.of(type));
  }
}
