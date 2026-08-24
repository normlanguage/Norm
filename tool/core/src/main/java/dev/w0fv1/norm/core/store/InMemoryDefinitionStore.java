package dev.w0fv1.norm.core.store;

import dev.w0fv1.norm.core.DefinitionGroupId;
import dev.w0fv1.norm.core.DefinitionHasher;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryDefinitionStore implements DefinitionStore {
  public InMemoryDefinitionStore() {}

  private final ConcurrentMap<DefinitionGroupId, byte[]> groups = new ConcurrentHashMap<>();

  @Override
  public PutResult put(byte[] canonicalGroup) throws IOException {
    Objects.requireNonNull(canonicalGroup, "canonicalGroup");
    byte[] ownedGroup = canonicalGroup.clone();
    DefinitionGroupId id = DefinitionHasher.hashGroup(ownedGroup);
    byte[] existing = groups.putIfAbsent(id, ownedGroup);
    if (existing != null) {
      verify(id, existing);
      if (!Arrays.equals(existing, ownedGroup)) {
        throw new IOException("distinct definition groups have the same content hash: " + id);
      }
      return new PutResult(id, PutResult.Status.REUSED);
    }
    return new PutResult(id, PutResult.Status.STORED);
  }

  @Override
  public Optional<byte[]> get(DefinitionGroupId id) throws IOException {
    Objects.requireNonNull(id, "id");
    byte[] canonicalGroup = groups.get(id);
    if (canonicalGroup == null) {
      return Optional.empty();
    }
    verify(id, canonicalGroup);
    return Optional.of(canonicalGroup.clone());
  }

  private static void verify(DefinitionGroupId expected, byte[] canonicalGroup)
      throws CorruptDefinitionException {
    DefinitionGroupId actual = DefinitionHasher.hashGroup(canonicalGroup);
    if (!expected.equals(actual)) {
      throw new CorruptDefinitionException(expected, actual);
    }
  }
}
