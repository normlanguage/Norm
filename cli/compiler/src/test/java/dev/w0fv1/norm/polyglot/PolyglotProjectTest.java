package dev.w0fv1.norm.polyglot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PolyglotProjectTest {
  @Test
  void reusesCapturedApplicationInputsByContent(@TempDir Path directory) throws Exception {
    Path module = Files.createDirectories(directory.resolve("sample"));
    Files.writeString(
        module.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1) }");
    Path entry = Files.writeString(module.resolve("main.norm"), "package sample Void main() {}");
    Path resource = Files.createDirectories(module.resolve("resources")).resolve("value.txt");
    Files.writeString(resource, "first");
    Path firstClasses;
    Path secondClasses;
    try (var context = Context.newBuilder("norm").build()) {
      context.initialize("norm");
      context.enter();
      try {
        var language = Language.context(null);
        var firstSources = language.projects().load(entry);
        var firstInput =
            new dev.w0fv1.norm.application.ApplicationInput(
                firstSources.applicationCompilationRequest(entry),
                java.util.Optional.of(firstSources));
        var first = language.application(firstInput);
        var againSources = language.projects().load(entry);
        var againInput =
            new dev.w0fv1.norm.application.ApplicationInput(
                againSources.applicationCompilationRequest(entry),
                java.util.Optional.of(againSources));
        assertEquals(firstInput, againInput);
        assertEquals(firstInput.hashCode(), againInput.hashCode());
        assertSame(first, language.application(againInput));
        Files.writeString(resource, "second");
        var secondSources = language.projects().load(entry);
        var second =
            language.application(
                new dev.w0fv1.norm.application.ApplicationInput(
                    secondSources.applicationCompilationRequest(entry),
                    java.util.Optional.of(secondSources)));
        assertNotSame(first, second);
        firstClasses = first.annotations().classes();
        secondClasses = second.annotations().classes();
        assertEquals("first", Files.readString(firstClasses.resolve("value.txt")));
        assertEquals("second", Files.readString(secondClasses.resolve("value.txt")));
      } finally {
        context.leave();
      }
    }
    assertFalse(Files.exists(firstClasses));
    assertFalse(Files.exists(secondClasses));
  }

  @Test
  void preparesApplicationDirectoryForFileSources(@TempDir Path directory) throws Exception {
    Path entry =
        Files.writeString(
            directory.resolve("main.norm"),
            "import std.application.applicationDirectory Void main() { printLine(applicationDirectory().value) }");
    var output = new ByteArrayOutputStream();
    try (var context = Context.newBuilder("norm").out(output).build()) {
      context.eval(Source.newBuilder("norm", entry.toFile()).build());
    }
    assertEquals(
        directory.toString() + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
  }
}
