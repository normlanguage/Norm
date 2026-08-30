package dev.w0fv1.norm.core;

import java.util.Objects;

public record CoreDefinitionRecord(DefinitionId id, CoreDefinition definition) {
  public CoreDefinitionRecord {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(definition, "definition");
  }
}
