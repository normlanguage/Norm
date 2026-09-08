package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CommandHelpTest {
  @Test
  void everyCommandProvidesHelpWithoutLoadingInputs() {
    for (String command :
        List.of(
            "help",
            "version",
            "setup",
            "build",
            "run",
            "test",
            "check",
            "query",
            "refactor",
            "resolve",
            "package",
            "docs",
            "lsp")) {
      for (String flag : List.of("-h", "--help")) {
        var out = new StringWriter();
        var err = new StringWriter();
        int result =
            new CliController()
                .run(new String[] {command, flag}, new PrintWriter(out), new PrintWriter(err));
        assertEquals(0, result, command + ": " + err);
        assertTrue(out.toString().contains("Usage:"), command);
        assertEquals("", err.toString());
      }
    }
  }
}
