package dev.w0fv1.norm.core.store;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileArtifactCacheTest {
  @TempDir Path directory;

  @Test
  void independentProcessesPublishCompleteArtifacts() throws Exception {
    Path probe =
        Path.of(
            FileArtifactCacheProbe.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
    var command =
        java.util.List.of(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "--module-path",
            System.getProperty("norm.test.modulePath"),
            "--patch-module",
            "dev.w0fv1.norm=" + probe,
            "--module",
            "dev.w0fv1.norm/" + FileArtifactCacheProbe.class.getName(),
            directory.resolve("cache").toString());
    var processes = new java.util.ArrayList<Process>();
    try {
      for (int index = 0; index < 2; index++) {
        processes.add(
            new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("process-" + index + ".log").toFile())
                .start());
      }
      for (int index = 0; index < processes.size(); index++) {
        var process = processes.get(index);
        assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(
            0,
            process.exitValue(),
            Files.readString(directory.resolve("process-" + index + ".log")));
      }
    } finally {
      for (var process : processes) if (process.isAlive()) process.destroyForcibly();
    }
  }

  @Test
  void boundsCapacityAndRejectsMisassociatedOrDamagedContent() throws Exception {
    var cache = new FileArtifactCache(directory, 2, 400);
    var first = Sha256Digest.compute(new byte[] {1});
    var second = Sha256Digest.compute(new byte[] {2});
    var third = Sha256Digest.compute(new byte[] {3});
    cache.write(first, new byte[] {1});
    cache.write(second, new byte[] {2});
    cache.write(third, new byte[] {3});
    assertArrayEquals(new byte[] {3}, cache.read(third).orElseThrow());
    try (var files = Files.list(directory)) {
      assertEquals(2, files.filter(path -> path.toString().endsWith(".bin")).count());
    }
    Files.copy(
        directory.resolve(third.value() + ".bin"),
        directory.resolve(first.value() + ".bin"),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    assertTrue(cache.read(first).isEmpty());
    Files.writeString(directory.resolve(third.value() + ".bin"), "broken");
    assertTrue(cache.read(third).isEmpty());
    cache.write(third, new byte[] {3});
    assertArrayEquals(new byte[] {3}, cache.read(third).orElseThrow());
  }

  @Test
  void concurrentInstancesPublishCompleteArtifacts() throws Exception {
    var first = new FileArtifactCache(directory, 10, 10000);
    var second = new FileArtifactCache(directory, 10, 10000);
    byte[] payload = new byte[1024];
    var key = Sha256Digest.compute(payload);
    try (var workers = Executors.newFixedThreadPool(2)) {
      var writer =
          workers.submit(
              () -> {
                for (int index = 0; index < 20; index++) first.write(key, payload);
                return null;
              });
      var reader =
          workers.submit(
              () -> {
                for (int index = 0; index < 20; index++) {
                  var value = second.read(key);
                  if (value.isPresent()) assertArrayEquals(payload, value.orElseThrow());
                }
                return null;
              });
      writer.get();
      reader.get();
    }
    assertArrayEquals(payload, second.read(key).orElseThrow());
  }
}
