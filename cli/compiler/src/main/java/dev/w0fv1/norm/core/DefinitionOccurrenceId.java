package dev.w0fv1.norm.core;

import java.util.Objects;

public record DefinitionOccurrenceId(DefinitionId representative, int ordinal)
    implements Comparable<DefinitionOccurrenceId> {
  public DefinitionOccurrenceId {
    Objects.requireNonNull(representative, "representative");
    if (ordinal < 0) throw new IllegalArgumentException("occurrence ordinal must not be negative");
  }

  @Override
  public int compareTo(DefinitionOccurrenceId other) {
    int definitionOrder =
        representative.compareTo(Objects.requireNonNull(other, "other").representative);
    return definitionOrder != 0 ? definitionOrder : Integer.compare(ordinal, other.ordinal);
  }
}
