package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;

import org.junit.jupiter.api.Test;

final class ProcessTest {
  @Test
  void runsRealGitAndReportsStartFailure() {
    assertOutput(
        """
        import std.process.runProcess
        import std.process.ProcessException
        import std.io.decodeText
        import std.io.TextEncoding
        Void main() {
          var result = runProcess(executable: "git", arguments: ["--version"])
          printLine(result.exitCode ?? -1)
          printLine(result.outcome)
          printLine(decodeText(content: result.stdout, encoding: TextEncoding.Utf8).startsWith(prefix: "git version"))
          try { runProcess(executable: "norm-nonexistent-executable-123456") }
          catch ProcessException error { printLine(error.code) }
          Map<String, String> environment = Map<>()
          environment.put(key: "bad=name", value: "value")
          try { runProcess(executable: "git", environment: environment) }
          catch ProcessException error { printLine(error.code) }
        }
        """,
        "0",
        "ProcessOutcome.Exited",
        "true",
        "NORM-PROCESS-START-FAILED",
        "NORM-PROCESS-INVALID-REQUEST");
  }
}
