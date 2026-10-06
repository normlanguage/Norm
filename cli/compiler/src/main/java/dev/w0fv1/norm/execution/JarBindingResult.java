package dev.w0fv1.norm.execution;

import java.util.List;
import java.util.Objects;

public sealed interface JarBindingResult
    permits JarBindingResult.Null,
        JarBindingResult.ClassReference,
        JarBindingResult.EnumReference,
        JarBindingResult.Reference,
        JarBindingResult.ResourceClosed,
        JarBindingResult.ResourceReference,
        JarBindingResult.BorrowedReference,
        JarBindingResult.ReceiverAlias,
        JarBindingResult.Scalar,
        JarBindingResult.Void {
  record ClassReference(List<JarBindingClassReference> candidates) implements JarBindingResult {
    public ClassReference {
      candidates = List.copyOf(candidates);
      if (candidates.isEmpty()) {
        throw new IllegalArgumentException("JAR class result requires a Norm class candidate");
      }
    }
  }

  record EnumReference(JarBindingEnumValue value) implements JarBindingResult {
    public EnumReference {
      Objects.requireNonNull(value, "value");
    }
  }

  record Scalar(Object value) implements JarBindingResult {
    public Scalar {
      Objects.requireNonNull(value, "value");
    }
  }

  record Reference(
      Object value, String displayName, List<JarBindingClassReference.Nominal> candidates)
      implements JarBindingResult {
    public Reference {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(displayName, "displayName");
      candidates = List.copyOf(candidates);
    }

    public Reference(Object value, String displayName) {
      this(value, displayName, List.of());
    }
  }

  record ReceiverAlias(JarBindingResult value) implements JarBindingResult {
    public ReceiverAlias {
      Objects.requireNonNull(value, "value");
    }
  }

  record BorrowedReference(
      Object value, String displayName, List<JarBindingClassReference.Nominal> candidates)
      implements JarBindingResult {
    public BorrowedReference {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(displayName, "displayName");
      candidates = List.copyOf(candidates);
    }
  }

  record ResourceReference(
      AutoCloseable value, String displayName, List<JarBindingClassReference.Nominal> candidates)
      implements JarBindingResult {
    public ResourceReference {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(displayName, "displayName");
      candidates = List.copyOf(candidates);
    }

    public ResourceReference(AutoCloseable value, String displayName) {
      this(value, displayName, List.of());
    }
  }

  enum Null implements JarBindingResult {
    INSTANCE
  }

  enum Void implements JarBindingResult {
    INSTANCE
  }

  enum ResourceClosed implements JarBindingResult {
    INSTANCE
  }
}
