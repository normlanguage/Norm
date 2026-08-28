package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import java.util.List;
import java.util.Objects;

public sealed interface BoundAnnotationReference extends BoundAnnotationValue.Content
    permits BoundAnnotationReference.ClassReference,
        BoundAnnotationReference.CallableReference,
        BoundAnnotationReference.FieldReference {
  record ClassReference(SemanticType reflectedType) implements BoundAnnotationReference {
    public ClassReference {
      Objects.requireNonNull(reflectedType, "reflectedType");
    }
  }

  record CallableReference(
      BoundCallableId callable,
      List<SemanticType> receiverTypeArguments,
      List<SemanticType> reifiedArguments,
      boolean virtual)
      implements BoundAnnotationReference {
    public CallableReference {
      Objects.requireNonNull(callable, "callable");
      receiverTypeArguments = List.copyOf(receiverTypeArguments);
      reifiedArguments = List.copyOf(reifiedArguments);
    }
  }

  record FieldReference(
      BoundFieldId field, int ordinal, SemanticType ownerType, SemanticType valueType)
      implements BoundAnnotationReference {
    public FieldReference {
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(ownerType, "ownerType");
      Objects.requireNonNull(valueType, "valueType");
      if (ordinal < 0) throw new IllegalArgumentException("field ordinal must not be negative");
    }
  }
}
