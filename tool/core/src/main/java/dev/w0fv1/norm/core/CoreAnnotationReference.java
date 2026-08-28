package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public sealed interface CoreAnnotationReference extends CoreAnnotationValue.Content
    permits CoreAnnotationReference.ClassReference,
        CoreAnnotationReference.CallableReference,
        CoreAnnotationReference.FieldReference {
  record ClassReference(CoreType reflectedType) implements CoreAnnotationReference {
    public ClassReference {
      Objects.requireNonNull(reflectedType, "reflectedType");
    }
  }

  record CallableReference(
      CoreDefinitionLink callable,
      List<CoreType> receiverTypeArguments,
      List<CoreType> reifiedArguments,
      boolean virtual)
      implements CoreAnnotationReference {
    public CallableReference {
      Objects.requireNonNull(callable, "callable");
      receiverTypeArguments = List.copyOf(receiverTypeArguments);
      reifiedArguments = List.copyOf(reifiedArguments);
    }
  }

  record FieldReference(int ordinal, CoreType ownerType, CoreType valueType)
      implements CoreAnnotationReference {
    public FieldReference {
      if (ordinal < 0) throw new IllegalArgumentException("field ordinal must not be negative");
      Objects.requireNonNull(ownerType, "ownerType");
      Objects.requireNonNull(valueType, "valueType");
    }
  }
}
