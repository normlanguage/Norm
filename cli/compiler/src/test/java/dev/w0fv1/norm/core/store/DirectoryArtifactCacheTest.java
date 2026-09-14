package dev.w0fv1.norm.core.store;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DirectoryArtifactCacheTest {
  @TempDir Path directory;

  @Test
  void pendingProcessPublicationSurvivesUnrelatedEviction() throws Exception {
    Path probe =
        Path.of(
            DirectoryArtifactCacheProbe.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--module-path",
                System.getProperty("norm.test.modulePath"),
                "--patch-module",
                "dev.w0fv1.norm=" + probe,
                "--module",
                "dev.w0fv1.norm/" + DirectoryArtifactCacheProbe.class.getName(),
                directory.toString(),
                "produce")
            .redirectError(directory.resolve("child.err").toFile())
            .start();
    try (var workers = Executors.newFixedThreadPool(2)) {
      var input = process.inputReader();
      var ready = workers.submit(input::readLine);
      Path pending = Path.of(ready.get(10, java.util.concurrent.TimeUnit.SECONDS));
      var cache = new DirectoryArtifactCache(directory, 1, 1024);
      try {
        var independent =
            workers.submit(
                () -> {
                  try (var lease =
                      cache.acquire(
                          Sha256Digest.compute(new byte[] {2}),
                          root -> true,
                          root -> Files.writeString(root.resolve("value"), "other"))) {
                    assertEquals("other", Files.readString(lease.path().resolve("value")));
                  }
                  return null;
                });
        independent.get(3, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("partial", Files.readString(pending.resolve("value")));
      } finally {
        process.getOutputStream().write(1);
        process.getOutputStream().flush();
      }
      assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(0, process.exitValue(), Files.readString(directory.resolve("child.err")));
      try (var lease =
          cache.acquire(
              Sha256Digest.compute(new byte[] {1}),
              root -> Files.readString(root.resolve("value")).equals("child"),
              root -> {
                throw new IOException("completed publication must be reused");
              })) {
        assertEquals(pending, lease.path());
      }
    } finally {
      if (process.isAlive()) process.destroyForcibly().waitFor();
    }
  }

  @Test
  void independentKeysCanPublishWhileAnotherProducerOrValidatorIsBlocked() throws Exception {
    for (boolean validating : new boolean[] {false, true}) {
      var cache =
          new DirectoryArtifactCache(directory.resolve(Boolean.toString(validating)), 1, 1024);
      var slowKey = Sha256Digest.compute(new byte[] {11});
      if (validating) {
        try (var seed =
            cache.acquire(
                slowKey, root -> true, root -> Files.writeString(root.resolve("value"), "slow"))) {
          assertTrue(Files.isRegularFile(seed.path().resolve("value")));
        }
      }
      var entered = new java.util.concurrent.CountDownLatch(1);
      var release = new java.util.concurrent.CountDownLatch(1);
      DirectoryArtifactCache.Producer waiting =
          root -> {
            entered.countDown();
            try {
              if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS))
                throw new IOException("release timed out");
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
              throw new IOException(interrupted);
            }
          };
      try (var workers = Executors.newFixedThreadPool(2)) {
        var slow =
            workers.submit(
                () -> {
                  try (var lease =
                      cache.acquire(
                          slowKey,
                          root -> {
                            if (validating) waiting.write(root);
                            return true;
                          },
                          root -> {
                            waiting.write(root);
                            Files.writeString(root.resolve("value"), "slow");
                          })) {
                    assertEquals("slow", Files.readString(lease.path().resolve("value")));
                  }
                  return null;
                });
        try {
          assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
          var fast =
              workers.submit(
                  () -> {
                    try (var lease =
                        cache.acquire(
                            Sha256Digest.compute(new byte[] {12}),
                            root -> true,
                            root -> Files.writeString(root.resolve("value"), "fast"))) {
                      assertEquals("fast", Files.readString(lease.path().resolve("value")));
                    }
                    return null;
                  });
          fast.get(2, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
          release.countDown();
        }
        slow.get(5, java.util.concurrent.TimeUnit.SECONDS);
      }
    }
  }

  @Test
  void processLeaseSurvivesEvictionAndIsReclaimedAfterProcessDeath() throws Exception {
    Path probe =
        Path.of(
            DirectoryArtifactCacheProbe.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    var process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--module-path",
                System.getProperty("norm.test.modulePath"),
                "--patch-module",
                "dev.w0fv1.norm=" + probe,
                "--module",
                "dev.w0fv1.norm/" + DirectoryArtifactCacheProbe.class.getName(),
                directory.toString())
            .redirectError(directory.resolve("child.err").toFile())
            .start();
    try {
      var pathFuture =
          java.util.concurrent.CompletableFuture.supplyAsync(
              () -> {
                try {
                  return process.inputReader().readLine();
                } catch (IOException exception) {
                  throw new java.io.UncheckedIOException(exception);
                }
              });
      Path child = Path.of(pathFuture.get(20, java.util.concurrent.TimeUnit.SECONDS));
      var cache = new DirectoryArtifactCache(directory, 1, 1024);
      try (var newer =
          cache.acquire(
              Sha256Digest.compute(new byte[] {2}),
              root -> true,
              root -> Files.writeString(root.resolve("value"), "parent"))) {
        assertEquals("child", Files.readString(child.resolve("value")));
        process.destroyForcibly();
        assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS));
      }
      assertFalse(Files.exists(child));
    } finally {
      if (process.isAlive()) process.destroyForcibly().waitFor();
    }
  }

  @Test
  void concurrentReadersReuseOnePublicationAndKeepLeasedGenerations() throws Exception {
    var cache = new DirectoryArtifactCache(directory, 1, 1024);
    var writes = new AtomicInteger();
    var key = Sha256Digest.compute(new byte[] {1});
    DirectoryArtifactCache.Producer producer =
        root -> {
          writes.incrementAndGet();
          Files.writeString(root.resolve("value"), "first");
        };
    Path first;
    try (var lease =
        cache.acquire(
            key, root -> Files.readString(root.resolve("value")).equals("first"), producer)) {
      first = lease.path();
      try (var workers = Executors.newSingleThreadExecutor()) {
        workers
            .submit(
                () -> {
                  try (var other =
                      new DirectoryArtifactCache(directory, 1, 1024)
                          .acquire(key, root -> true, producer)) {
                    assertEquals(first, other.path());
                  }
                  return null;
                })
            .get();
      }
      try (var other =
          cache.acquire(
              Sha256Digest.compute(new byte[] {2}),
              root -> true,
              root -> Files.writeString(root.resolve("value"), "second"))) {
        assertEquals("first", Files.readString(first.resolve("value")));
      }
      assertTrue(Files.isDirectory(first));
    }
    assertEquals(1, writes.get());
    try (var entries = Files.list(directory)) {
      assertEquals(1, entries.filter(Files::isDirectory).count());
    }
  }

  @Test
  void corruptLeasedGenerationIsReplacedWithoutMutatingIt() throws Exception {
    var cache = new DirectoryArtifactCache(directory, 1, 1024);
    var key = Sha256Digest.compute(new byte[] {3});
    DirectoryArtifactCache.Validator validator =
        root -> Files.readString(root.resolve("value")).equals("valid");
    DirectoryArtifactCache.Producer producer =
        root -> Files.writeString(root.resolve("value"), "valid");
    try (var original = cache.acquire(key, validator, producer)) {
      Files.writeString(original.path().resolve("value"), "other");
      try (var repaired = cache.acquire(key, validator, producer)) {
        assertNotEquals(original.path(), repaired.path());
        assertEquals("other", Files.readString(original.path().resolve("value")));
        assertEquals("valid", Files.readString(repaired.path().resolve("value")));
      }
    }
  }

  @Test
  void failedPublicationIsRemovedAndByteLimitIsEnforcedAfterRelease() throws Exception {
    var cache = new DirectoryArtifactCache(directory, 10, 8);
    var key = Sha256Digest.compute(new byte[] {4});
    assertThrows(
        IOException.class,
        () ->
            cache.acquire(
                key,
                root -> true,
                root -> {
                  Files.writeString(root.resolve("partial"), "partial");
                  throw new IOException("producer failed");
                }));
    try (var entries = Files.list(directory)) {
      assertEquals(0, entries.filter(Files::isDirectory).count());
    }
    Path content;
    try (var lease =
        cache.acquire(
            key, root -> true, root -> Files.writeString(root.resolve("value"), "too large"))) {
      content = lease.path();
      assertTrue(Files.isDirectory(content));
    }
    assertFalse(Files.exists(content));
  }
}
