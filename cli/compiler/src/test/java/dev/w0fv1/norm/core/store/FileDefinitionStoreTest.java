package dev.w0fv1.norm.core.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.core.ContentHash;
import dev.w0fv1.norm.core.DefinitionGroupId;
import dev.w0fv1.norm.core.DefinitionHasher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileDefinitionStoreTest {
  @TempDir Path temporaryDirectory;

  @Test
  void storesGroupsByHashPrefixIdempotently() throws Exception {
    Path root = temporaryDirectory.resolve("definitions");
    DefinitionStore store = new FileDefinitionStore(root);
    byte[] canonicalGroup = "canonical-group".getBytes(StandardCharsets.UTF_8);

    PutBatchResult batch = store.putAll(List.of(canonicalGroup, canonicalGroup));
    PutResult first = batch.results().get(0);
    PutResult second = batch.results().get(1);
    String hash = first.id().toString();
    Path storedGroup = root.resolve(hash.substring(0, 2)).resolve(hash.substring(2));

    assertEquals(DefinitionHasher.hashGroup(canonicalGroup), first.id());
    assertEquals(first.id(), second.id());
    assertEquals(PutResult.Status.STORED, first.status());
    assertEquals(PutResult.Status.REUSED, second.status());
    assertArrayEquals(canonicalGroup, Files.readAllBytes(storedGroup));
    try (var paths = Files.list(storedGroup.getParent())) {
      assertEquals(List.of(storedGroup), paths.toList());
    }
  }

  @Test
  void ownsStoredAndReturnedBytes() throws Exception {
    Path root = temporaryDirectory.resolve("definitions");
    DefinitionStore store = new FileDefinitionStore(root);
    byte[] canonicalGroup = {1, 2, 3};
    DefinitionGroupId id = put(store, canonicalGroup).id();
    Path storedGroup = storedPath(root, id);
    Files.setLastModifiedTime(storedGroup, FileTime.fromMillis(1));

    canonicalGroup[0] = 9;
    byte[] returned = store.get(id).orElseThrow();
    returned[1] = 9;

    assertArrayEquals(new byte[] {1, 2, 3}, store.get(id).orElseThrow());
    assertEquals(FileTime.fromMillis(1), Files.getLastModifiedTime(storedGroup));
  }

  @Test
  void preservesCorruptEntriesUntilPutAtomicallyRepairsThem() throws Exception {
    Path root = temporaryDirectory.resolve("definitions");
    DefinitionStore reader = new FileDefinitionStore(root);
    DefinitionStore writer = new FileDefinitionStore(root);
    byte[] canonicalGroup = {1, 2, 3};
    DefinitionGroupId id = put(writer, canonicalGroup).id();
    String hash = id.toString();
    Path storedGroup = root.resolve(hash.substring(0, 2)).resolve(hash.substring(2));
    byte[] corruptContent = {4, 5, 6};
    Files.write(storedGroup, corruptContent);
    FileTime corruptedAt = Files.getLastModifiedTime(storedGroup);

    CorruptDefinitionException corruption =
        assertThrows(CorruptDefinitionException.class, () -> reader.get(id));
    assertEquals(id, corruption.expected());
    assertEquals(DefinitionHasher.hashGroup(corruptContent), corruption.actual());
    assertArrayEquals(corruptContent, Files.readAllBytes(storedGroup));
    assertEquals(corruptedAt, Files.getLastModifiedTime(storedGroup));
    assertEquals(id, put(writer, canonicalGroup).id());
    assertArrayEquals(canonicalGroup, reader.get(id).orElseThrow());
    assertFalse(reader.get(new DefinitionGroupId(ContentHash.of(new byte[32]))).isPresent());
  }

  @Test
  void concurrentStoreInstancesSerializeCorruptEntryRepair() throws Exception {
    Path root = temporaryDirectory.resolve("definitions");
    FileDefinitionStore firstStore = new FileDefinitionStore(root);
    FileDefinitionStore secondStore = new FileDefinitionStore(root);
    byte[] canonicalGroup = new byte[4 * 1024 * 1024];
    DefinitionGroupId id = put(firstStore, canonicalGroup).id();
    Path storedGroup = storedPath(root, id);
    Files.write(storedGroup, new byte[] {9, 8, 7});
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<PutResult> first =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return put(firstStore, canonicalGroup);
              });
      Future<PutResult> second =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return put(secondStore, canonicalGroup);
              });
      ready.await();
      start.countDown();
      boolean disappeared = false;
      do {
        disappeared |= !Files.isRegularFile(storedGroup);
      } while (!first.isDone() || !second.isDone());

      PutResult firstResult = first.get();
      PutResult secondResult = second.get();
      assertEquals(id, firstResult.id());
      assertEquals(id, secondResult.id());
      assertEquals(
          1,
          List.of(firstResult.status(), secondResult.status()).stream()
              .filter(PutResult.Status.STORED::equals)
              .count());
      assertEquals(
          1,
          List.of(firstResult.status(), secondResult.status()).stream()
              .filter(PutResult.Status.REUSED::equals)
              .count());
      assertFalse(disappeared);
    }

    assertArrayEquals(canonicalGroup, firstStore.get(id).orElseThrow());
    assertArrayEquals(canonicalGroup, secondStore.get(id).orElseThrow());
    try (var paths = Files.list(storedGroup.getParent())) {
      assertEquals(List.of(storedGroup), paths.toList());
    }
  }

  @Test
  void concurrentWritersConvergeOnOneCompleteGroup() throws Exception {
    Path root = temporaryDirectory.resolve("definitions");
    byte[] canonicalGroup = "shared-canonical-group".getBytes(StandardCharsets.UTF_8);
    int writers = 32;
    CountDownLatch ready = new CountDownLatch(writers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<PutResult>> results = new ArrayList<>();

    try (var executor = Executors.newFixedThreadPool(writers)) {
      for (int writer = 0; writer < writers; writer++) {
        results.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  start.await();
                  return put(new FileDefinitionStore(root), canonicalGroup);
                }));
      }
      ready.await();
      start.countDown();
      DefinitionGroupId expected = DefinitionHasher.hashGroup(canonicalGroup);
      int stored = 0;
      int reused = 0;
      for (Future<PutResult> result : results) {
        PutResult value = result.get();
        assertEquals(expected, value.id());
        switch (value.status()) {
          case STORED -> stored++;
          case REUSED -> reused++;
          case NOT_ADMITTED -> throw new AssertionError("group must fit the store policy");
        }
      }
      assertEquals(1, stored);
      assertEquals(writers - 1, reused);
    }

    DefinitionGroupId id = DefinitionHasher.hashGroup(canonicalGroup);
    assertArrayEquals(canonicalGroup, new FileDefinitionStore(root).get(id).orElseThrow());
    Path prefixDirectory = root.resolve(id.toString().substring(0, 2));
    try (var paths = Files.list(prefixDirectory)) {
      assertEquals(1, paths.count());
    }
  }

  @Test
  void publishesCompleteGroupsBeforeWaitingForCapacityMaintenance() throws Exception {
    Path root = temporaryDirectory.resolve("independent-publishing");
    FileDefinitionStore store = new FileDefinitionStore(root, 10, 1024);
    byte[] canonicalGroup = "independent-group".getBytes(StandardCharsets.UTF_8);
    DefinitionGroupId id = DefinitionHasher.hashGroup(canonicalGroup);
    Path storedGroup = storedPath(root, id);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<?> lockHolder =
          executor.submit(
              () -> {
                holdLock(root.resolve(".locks").resolve("maintenance"), locked, release);
                return null;
              });
      assertTrue(locked.await(3, TimeUnit.SECONDS));
      Future<PutResult> publication = executor.submit(() -> put(store, canonicalGroup));
      try {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
        while (!Files.isRegularFile(storedGroup) && System.nanoTime() < deadline) {
          Thread.onSpinWait();
        }
        assertTrue(Files.isRegularFile(storedGroup));
        assertArrayEquals(canonicalGroup, Files.readAllBytes(storedGroup));
        assertFalse(publication.isDone());
      } finally {
        release.countDown();
      }
      assertEquals(id, publication.get().id());
      lockHolder.get();
    }
  }

  @Test
  void blockedShardDoesNotBlockPublicationToAnotherShard() throws Exception {
    Path root = temporaryDirectory.resolve("parallel-shards");
    FileDefinitionStore firstStore = new FileDefinitionStore(root, 10, 1024);
    FileDefinitionStore secondStore = new FileDefinitionStore(root, 10, 1024);
    byte[] firstGroup = "blocked-group".getBytes(StandardCharsets.UTF_8);
    DefinitionGroupId firstId = DefinitionHasher.hashGroup(firstGroup);
    byte[] secondGroup = distinctShardGroup(firstId);
    DefinitionGroupId secondId = DefinitionHasher.hashGroup(secondGroup);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(3)) {
      Path shardLock = root.resolve(".locks").resolve(firstId.toString().substring(0, 2));
      Future<?> lockHolder =
          executor.submit(
              () -> {
                holdLock(shardLock, locked, release);
                return null;
              });
      assertTrue(locked.await(3, TimeUnit.SECONDS));
      Future<PutResult> blockedPublication = executor.submit(() -> put(firstStore, firstGroup));
      try {
        Future<PutResult> independentPublication =
            executor.submit(() -> put(secondStore, secondGroup));
        assertEquals(secondId, independentPublication.get(3, TimeUnit.SECONDS).id());
        assertFalse(blockedPublication.isDone());
      } finally {
        release.countDown();
      }
      assertEquals(firstId, blockedPublication.get().id());
      lockHolder.get();
    }
  }

  @Test
  void sameShardPrefixInAnotherStoreRootRemainsIndependent() throws Exception {
    Path firstRoot = temporaryDirectory.resolve("first-root");
    Path secondRoot = temporaryDirectory.resolve("second-root");
    new FileDefinitionStore(firstRoot, 10, 1024);
    FileDefinitionStore secondStore = new FileDefinitionStore(secondRoot, 10, 1024);
    byte[] canonicalGroup = "same-prefix".getBytes(StandardCharsets.UTF_8);
    DefinitionGroupId id = DefinitionHasher.hashGroup(canonicalGroup);
    String prefix = id.toString().substring(0, 2);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<?> lockHolder =
          executor.submit(
              () -> {
                holdLock(firstRoot.resolve(".locks").resolve(prefix), locked, release);
                return null;
              });
      assertTrue(locked.await(3, TimeUnit.SECONDS));
      try {
        Future<PutResult> publication = executor.submit(() -> put(secondStore, canonicalGroup));
        assertEquals(id, publication.get(3, TimeUnit.SECONDS).id());
      } finally {
        release.countDown();
      }
      lockHolder.get();
    }
  }

  @Test
  void concurrentPruningAndPutLeaveOnlyCompleteGroups() throws Exception {
    Path root = temporaryDirectory.resolve("pruned-definitions");
    byte[] hotGroup = new byte[32 * 1024 * 1024];
    hotGroup[0] = 1;
    byte[] retainedGroup = {2};
    FileDefinitionStore seed = new FileDefinitionStore(root, 2, 64L * 1024 * 1024);
    DefinitionGroupId hotId = put(seed, hotGroup).id();
    DefinitionGroupId retainedId = put(seed, retainedGroup).id();
    Path hotPath = storedPath(root, hotId);
    Files.setLastModifiedTime(hotPath, FileTime.fromMillis(1));
    Files.setLastModifiedTime(storedPath(root, retainedId), FileTime.fromMillis(2));
    FileDefinitionStore writer = new FileDefinitionStore(root, 2, 64L * 1024 * 1024);
    FileDefinitionStore pruner = new FileDefinitionStore(root, 2, 64L * 1024 * 1024);
    byte[] triggerGroup;
    int discriminator = 0;
    do {
      triggerGroup = ("trigger-" + discriminator++).getBytes(StandardCharsets.UTF_8);
    } while (DefinitionHasher.hashGroup(triggerGroup)
        .toString()
        .substring(0, 2)
        .equals(hotId.toString().substring(0, 2)));
    byte[] publishedTrigger = triggerGroup;
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<PutResult> hotPut =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return put(writer, hotGroup);
              });
      Future<PutResult> pruningPut =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return put(pruner, publishedTrigger);
              });
      ready.await();
      start.countDown();

      assertEquals(hotId, hotPut.get().id());
      assertEquals(DefinitionHasher.hashGroup(publishedTrigger), pruningPut.get().id());
    }

    var remaining = writer.get(hotId);
    if (remaining.isPresent()) assertArrayEquals(hotGroup, remaining.orElseThrow());
    assertEquals(hotId, put(writer, hotGroup).id());
    assertArrayEquals(hotGroup, writer.get(hotId).orElseThrow());
  }

  @Test
  void rejectsNullsAndNonDirectoryRoots() throws Exception {
    Path file = temporaryDirectory.resolve("file");
    Files.writeString(file, "content");

    assertThrows(NullPointerException.class, () -> new FileDefinitionStore(null));
    assertThrows(FileAlreadyExistsException.class, () -> new FileDefinitionStore(file));

    DefinitionStore store = new FileDefinitionStore(temporaryDirectory.resolve("definitions"));
    assertThrows(NullPointerException.class, () -> store.putAll(null));
    assertThrows(
        NullPointerException.class, () -> store.putAll(java.util.Arrays.asList((byte[]) null)));
    assertThrows(NullPointerException.class, () -> store.get(null));
  }

  @Test
  void evictsTheLeastRecentlyUsedGroupsAtTheConfiguredCapacity() throws Exception {
    Path root = temporaryDirectory.resolve("bounded-definitions");
    FileDefinitionStore store = new FileDefinitionStore(root, 2, 1024);
    DefinitionGroupId first = put(store, new byte[] {1}).id();
    DefinitionGroupId second = put(store, new byte[] {2}).id();
    Files.setLastModifiedTime(storedPath(root, first), FileTime.fromMillis(1));
    Files.setLastModifiedTime(storedPath(root, second), FileTime.fromMillis(2));
    store = new FileDefinitionStore(root, 2, 1024);
    store.get(first).orElseThrow();
    DefinitionGroupId third = put(store, new byte[] {3}).id();

    assertFalse(store.get(second).isPresent());
    assertArrayEquals(new byte[] {1}, store.get(first).orElseThrow());
    assertArrayEquals(new byte[] {3}, store.get(third).orElseThrow());
  }

  @Test
  void enforcesCapacityAfterPublishingTheWholeBatch() throws Exception {
    Path root = temporaryDirectory.resolve("batch-bounded-definitions");
    FileDefinitionStore store = new FileDefinitionStore(root, 2, 1024);
    byte[] first = {1};
    byte[] second = {2};
    byte[] third = {3};

    PutBatchResult batch = store.putAll(List.of(first, second, third));

    assertEquals(
        List.of(PutResult.Status.STORED, PutResult.Status.STORED, PutResult.Status.STORED),
        batch.results().stream().map(PutResult::status).toList());
    int retained = 0;
    for (PutResult result : batch.results()) {
      if (store.get(result.id()).isPresent()) retained++;
    }
    assertEquals(2, retained);
    assertArrayEquals(third, store.get(batch.results().get(2).id()).orElseThrow());
  }

  @Test
  void evictsGroupsWhenTheByteCapacityIsExceeded() throws Exception {
    Path root = temporaryDirectory.resolve("byte-bounded-definitions");
    FileDefinitionStore store = new FileDefinitionStore(root, 10, 3);
    DefinitionGroupId first = put(store, new byte[] {1, 2}).id();
    Files.setLastModifiedTime(storedPath(root, first), FileTime.fromMillis(1));
    store = new FileDefinitionStore(root, 10, 3);
    DefinitionGroupId second = put(store, new byte[] {3, 4}).id();

    assertFalse(store.get(first).isPresent());
    assertArrayEquals(new byte[] {3, 4}, store.get(second).orElseThrow());
  }

  @Test
  void enforcesOneCapacityPolicyAcrossStoreInstances() throws Exception {
    Path root = temporaryDirectory.resolve("shared-bounded-definitions");
    FileDefinitionStore firstStore = new FileDefinitionStore(root, 1, 1024);
    FileDefinitionStore secondStore = new FileDefinitionStore(root, 1, 1024);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    PutResult first;
    PutResult second;

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<PutResult> firstPut =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return put(firstStore, new byte[] {1});
              });
      Future<PutResult> secondPut =
          executor.submit(
              () -> {
                ready.countDown();
                start.await();
                return put(secondStore, new byte[] {2});
              });
      ready.await();
      start.countDown();
      first = firstPut.get();
      second = secondPut.get();
    }

    assertEquals(PutResult.Status.STORED, first.status());
    assertEquals(PutResult.Status.STORED, second.status());
    assertEquals(
        1,
        List.of(firstStore.get(first.id()), secondStore.get(second.id())).stream()
            .filter(Optional::isPresent)
            .count());
  }

  @Test
  void enforcesTheSharedByteCapacityAcrossStoreInstances() throws Exception {
    Path root = temporaryDirectory.resolve("shared-byte-bounded-definitions");
    FileDefinitionStore firstStore = new FileDefinitionStore(root, 10, 3);
    FileDefinitionStore secondStore = new FileDefinitionStore(root, 10, 3);

    PutResult first = put(firstStore, new byte[] {1, 2});
    PutResult second = put(secondStore, new byte[] {3, 4});

    assertTrue(secondStore.get(first.id()).isEmpty());
    assertArrayEquals(new byte[] {3, 4}, secondStore.get(second.id()).orElseThrow());
  }

  @Test
  void ignoresFilesOutsideTheCanonicalGroupPathShape() throws Exception {
    Path root = temporaryDirectory.resolve("mixed-definitions");
    FileDefinitionStore store = new FileDefinitionStore(root, 1, 1024);
    Path unrelated = root.resolve("a").resolve("0".repeat(63));
    Files.createDirectories(unrelated.getParent());
    Files.write(unrelated, new byte[] {9});

    PutResult stored = put(store, new byte[] {1});

    assertTrue(Files.isRegularFile(unrelated));
    assertArrayEquals(new byte[] {1}, store.get(stored.id()).orElseThrow());
  }

  @Test
  void doesNotAdmitGroupsLargerThanTheRootPolicy() throws Exception {
    Path root = temporaryDirectory.resolve("oversized-definitions");
    FileDefinitionStore store = new FileDefinitionStore(root, 10, 3);

    PutResult result = put(store, new byte[] {1, 2, 3, 4});

    assertEquals(DefinitionHasher.hashGroup(new byte[] {1, 2, 3, 4}), result.id());
    assertEquals(PutResult.Status.NOT_ADMITTED, result.status());
    assertTrue(store.get(result.id()).isEmpty());
    assertFalse(Files.exists(storedPath(root, result.id())));
  }

  @Test
  void rejectsDifferentPoliciesForTheSameCanonicalRoot() throws Exception {
    Path root = temporaryDirectory.resolve("policy-bound-definitions");
    new FileDefinitionStore(root, 1, 1024);

    DefinitionStorePolicyMismatchException groups =
        assertThrows(
            DefinitionStorePolicyMismatchException.class,
            () -> new FileDefinitionStore(root, 2, 1024));
    DefinitionStorePolicyMismatchException bytes =
        assertThrows(
            DefinitionStorePolicyMismatchException.class,
            () -> new FileDefinitionStore(root, 1, 2048));

    assertEquals(1, groups.configuredMaximumGroups());
    assertEquals(1024, groups.configuredMaximumBytes());
    assertEquals(2, groups.requestedMaximumGroups());
    assertEquals(1, bytes.requestedMaximumGroups());
    assertEquals(2048, bytes.requestedMaximumBytes());
  }

  private static Path storedPath(Path root, DefinitionGroupId id) {
    String hash = id.toString();
    return root.resolve(hash.substring(0, 2)).resolve(hash.substring(2));
  }

  private static void holdLock(Path path, CountDownLatch locked, CountDownLatch release)
      throws IOException {
    FileLockCoordinator.shared()
        .withLock(
            path,
            () -> {
              locked.countDown();
              try {
                release.await();
              } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(exception);
              }
              return null;
            });
  }

  private static byte[] distinctShardGroup(DefinitionGroupId other) {
    String excludedPrefix = other.toString().substring(0, 2);
    for (int discriminator = 0; ; discriminator++) {
      byte[] candidate = ("independent-" + discriminator).getBytes(StandardCharsets.UTF_8);
      if (!DefinitionHasher.hashGroup(candidate).toString().startsWith(excludedPrefix)) {
        return candidate;
      }
    }
  }

  private static PutResult put(DefinitionStore store, byte[] canonicalGroup) throws Exception {
    return store.putAll(List.of(canonicalGroup)).results().get(0);
  }
}
