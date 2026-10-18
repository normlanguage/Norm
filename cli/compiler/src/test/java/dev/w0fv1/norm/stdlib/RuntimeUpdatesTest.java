package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;

import org.junit.jupiter.api.Test;

final class RuntimeUpdatesTest {
  @Test
  void sourceUpdateCapabilitiesAreUnavailableInStandaloneExecution() {
    assertOutput(
        """
        import std.core.Exception
        import std.runtime.runtimeStatus
        import std.runtime.beginUpdate
        import std.runtime.publishUpdate
        import std.runtime.latestUpdate
        Void main() {
          try { runtimeStatus() }
          catch Exception error { printLine(error.message.contains("unavailable")) }
          try { beginUpdate() }
          catch Exception error { printLine(error.message.contains("unavailable")) }
          try { publishUpdate() }
          catch Exception error { printLine(error.message.contains("unavailable")) }
          try { latestUpdate() }
          catch Exception error { printLine(error.message.contains("unavailable")) }
        }
        """,
        "true",
        "true",
        "true",
        "true");
  }
}
