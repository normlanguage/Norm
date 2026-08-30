package dev.w0fv1.norm.core.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.core.ContentHash;
import dev.w0fv1.norm.core.DefinitionGroupId;
import dev.w0fv1.norm.core.DefinitionHasher;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

final class InMemoryDefinitionStoreTest {
  @Test
  void storesCanonicalGroupsIdempotently() throws Exception {
    DefinitionStore store = new InMemoryDefinitionStore();
    byte[] canonicalGroup = "canonical-group".getBytes(StandardCharsets.UTF_8);

    PutBatchResult batch = store.putAll(List.of(canonicalGroup, canonicalGroup));
    PutResult first = batch.results().get(0);
    PutResult second = batch.results().get(1);

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
    DefinitionGroupId id = store.putAll(List.of(canonicalGroup)).results().get(0).id();

    canonicalGroup[0] = 9;
    byte[] returned = store.get(id).orElseThrow();
    returned[1] = 9;

    assertArrayEquals(new byte[] {1, 2, 3}, store.get(id).orElseThrow());
  }

  @Test
  void rejectsNullKeysAndContent() {
    DefinitionStore store = new InMemoryDefinitionStore();

    assertThrows(NullPointerException.class, () -> store.putAll(null));
    assertThrows(
        NullPointerException.class, () -> store.putAll(java.util.Arrays.asList((byte[]) null)));
    assertThrows(NullPointerException.class, () -> store.get(null));
    assertThrows(NullPointerException.class, () -> new PutResult(null, PutResult.Status.STORED));
    assertThrows(
        NullPointerException.class,
        () -> new PutResult(new DefinitionGroupId(ContentHash.of(new byte[32])), null));
  }

  @Test
  void preservesBatchOrderAndOwnsItsResults() throws Exception {
    DefinitionStore store = new InMemoryDefinitionStore();
    byte[] first = {1};
    byte[] second = {2};

    PutBatchResult batch = store.putAll(List.of(first, second, first));

    assertEquals(
        List.of(
            DefinitionHasher.hashGroup(first),
            DefinitionHasher.hashGroup(second),
            DefinitionHasher.hashGroup(first)),
        batch.results().stream().map(PutResult::id).toList());
    assertEquals(
        List.of(PutResult.Status.STORED, PutResult.Status.STORED, PutResult.Status.REUSED),
        batch.results().stream().map(PutResult::status).toList());
    assertThrows(UnsupportedOperationException.class, () -> batch.results().clear());
  }
}
