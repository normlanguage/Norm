package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;

import org.junit.jupiter.api.Test;

final class RuntimeUpdatesTest {
  @Test
  void sourceUpdateCapabilitiesAreUnavailableInStandaloneExecution() {
    assertOutput(
        """
        import std.core.Exception
        import std.system.currentSystem
        Void main() {
          var runtime = currentSystem().runtime()
          try { runtime.status() }
          catch Exception error { printLine(error.message.contains("unavailable")) }
          try { runtime.beginUpdate() }
          catch Exception error { printLine(error.message.contains("unavailable")) }
          try {
            var batch = runtime.beginUpdate()
            batch.preview()
          }
          catch Exception error { printLine(error.message.contains("unavailable")) }
          try { runtime.publishUpdate() }
          catch Exception error { printLine(error.message.contains("unavailable")) }
          try { runtime.previewUpdate() }
          catch Exception error { printLine(error.message.contains("unavailable")) }
          try { runtime.latestUpdate() }
          catch Exception error { printLine(error.message.contains("unavailable")) }
          try { runtime.onUpdate { } }
          catch Exception error { printLine(error.message.contains("unavailable")) }
        }
        """,
        "true",
        "true",
        "true",
        "true",
        "true",
        "true",
        "true");
  }
}
