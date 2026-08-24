package dev.w0fv1.norm.bound;

import java.util.List;
import java.util.Objects;

public record BoundEnumVariant(BoundEnumVariantId id, String name, List<BoundEnumField> fields) {
  public BoundEnumVariant {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    fields = List.copyOf(fields);
  }
}
