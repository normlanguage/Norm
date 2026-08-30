package dev.w0fv1.norm.core;

import java.util.List;
import java.util.Objects;

public record CoreEnumVariant(String key, List<CoreField> fields) {
  public CoreEnumVariant {
    Objects.requireNonNull(key, "key");
    if (key.isBlank()) throw new IllegalArgumentException("enum variant key must not be blank");
    fields = List.copyOf(fields);
    for (int index = 0; index < fields.size(); index++) {
      if (fields.get(index).ordinal() != index) {
        throw new IllegalArgumentException("enum fields must be dense and ordered");
      }
    }
  }
}
