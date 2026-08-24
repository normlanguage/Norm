package dev.w0fv1.norm.core.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.core.ContentHash;
import dev.w0fv1.norm.core.DefinitionGroupId;
import dev.w0fv1.norm.core.DefinitionHasher;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class InMemoryDefinitionStoreTest {
  @Test
  void storesCanonicalGroupsIdempotently() throws Exception {
    DefinitionStore store = new InMemoryDefinitionStore();
    byte[] canonicalGroup = "canonical-group".getBytes(StandardCharsets.UTF_8);

    PutResult first = store.put(canonicalGroup);
    PutResult second = store.put(canonicalGroup);

    assertEquals(DefinitionHasher.hashGroup(canonicalGroup), first.id());
    assertEquals(first.id(), second.id());
    assertEquals(PutResult.Status.STORED, first.status());
    assertEquals(PutResult.Status.REUSED, second.status());
    assertArrayEquals(canonicalGroup, store.get(first.id()).orElseThrow());
    assertFalse(store.get(new DefinitionGroupId(ContentHash.of(new byte[32]))).isPresent());
  }

  @Test
  void ownsStoredAndReturnedBytes() throws Exception {
    DefinitionStore store = new InMemoryDefinitionStore();
    byte[] canonicalGroup = {1, 2, 3};
    DefinitionGroupId id = store.put(canonicalGroup).id();

    canonicalGroup[0] = 9;
    byte[] returned = store.get(id).orElseThrow();
    returned[1] = 9;

    assertArrayEquals(new byte[] {1, 2, 3}, store.get(id).orElseThrow());
  }

  @Test
  void rejectsNullKeysAndContent() {
    DefinitionStore store = new InMemoryDefinitionStore();

    assertThrows(NullPointerException.class, () -> store.put(null));
    assertThrows(NullPointerException.class, () -> store.get(null));
    assertThrows(NullPointerException.class, () -> new PutResult(null, PutResult.Status.STORED));
    assertThrows(
        NullPointerException.class,
        () -> new PutResult(new DefinitionGroupId(ContentHash.of(new byte[32])), null));
  }
}
