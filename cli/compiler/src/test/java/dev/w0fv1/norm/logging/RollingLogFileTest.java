package dev.w0fv1.norm.logging;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RollingLogFileTest {
  @Test
  void retainsActiveSessionsAndReclaimsClosedSegments(@TempDir Path root) throws Exception {
    byte[] event = "{\"v\":1}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    try (var first = new RollingLogFile(root, event.length, 7, event.length)) {
      first.write(event, 0, event.length);
      first.write(event, 0, event.length);
      try (var second = new RollingLogFile(root, event.length, 7, event.length)) {
        second.write(event, 0, event.length);
        try (var paths = Files.walk(root)) {
          assertEquals(3, paths.filter(p -> p.toString().endsWith(".jsonl")).count());
        }
      }
    }
    try (var third = new RollingLogFile(root, event.length, 7, event.length)) {
      try (var paths = Files.walk(root)) {
        assertEquals(1, paths.filter(p -> p.toString().endsWith(".jsonl")).count());
      }
    }
  }
}
