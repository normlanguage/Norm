package dev.w0fv1.norm.core;

import java.util.Arrays;
import java.util.List;

public final class CoreDefinitionGroup {
  private final DefinitionGroupId id;
  private final List<CoreDefinition> definitions;
  private final byte[] canonicalBytes;

  private CoreDefinitionGroup(
      DefinitionGroupId id, List<CoreDefinition> definitions, byte[] canonicalBytes) {
    this.id = id;
    this.definitions = definitions;
    this.canonicalBytes = canonicalBytes;
  }

  public static CoreDefinitionGroup create(List<CoreDefinition> definitions) {
    List<CoreDefinition> stable = List.copyOf(definitions);
    if (stable.isEmpty()) throw new IllegalArgumentException("definition group must not be empty");
    CoreValidation.requireResolved(stable);
    byte[] canonical = CoreCodec.encodeGroup(stable);
    return new CoreDefinitionGroup(DefinitionHasher.hashGroup(canonical), stable, canonical);
  }

  public DefinitionGroupId id() {
    return id;
  }

  public List<CoreDefinition> definitions() {
    return definitions;
  }

  public byte[] canonicalBytes() {
    return canonicalBytes.clone();
  }

  public DefinitionId definitionId(int memberIndex) {
    if (memberIndex < 0 || memberIndex >= definitions.size()) {
      throw new IllegalArgumentException("definition member is outside its group");
    }
    return new DefinitionId(id, memberIndex);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || other instanceof CoreDefinitionGroup group
            && id.equals(group.id)
            && Arrays.equals(canonicalBytes, group.canonicalBytes);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
