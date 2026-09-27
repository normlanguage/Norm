package dev.w0fv1.norm.cli.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.cli.value.ExitCode;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class HelloCommandTest {
  @TempDir Path directory;

  @Test
  void generatesEveryExampleAndBilingualGuide() throws IOException {
    var out = new StringWriter();
    var err = new StringWriter();

    int result =
        new HelloCommand(directory).execute(List.of(), new PrintWriter(out), new PrintWriter(err));

    assertEquals(ExitCode.SUCCESS, result, err.toString());
    assertEquals("", err.toString());
    for (String name :
        List.of(
            "hell.norm",
            "sort.norm",
            "maze.norm",
            "todo.norm",
            "board.norm",
            "README.md",
            "README.zh-CN.md")) {
      assertTrue(Files.size(directory.resolve(name)) > 0, name);
      assertTrue(out.toString().contains(name), name);
    }
    for (Map.Entry<String, String> example :
        Map.of(
                "hell.norm",
                "Hello, Norm\n",
                "sort.norm",
                "Original:\n5\n1\n8\n3\nSorted:\n1\n3\n5\n8\n",
                "maze.norm",
                "Shortest route:\nS.#..\n*.#.#\n***#.\n.#***\n...#E\n")
            .entrySet()) {
      var actualOut = new StringWriter();
      var actualErr = new StringWriter();
      int runResult =
          new CliController()
              .run(
                  new String[] {"run", directory.resolve(example.getKey()).toString()},
                  new PrintWriter(actualOut),
                  new PrintWriter(actualErr));
      assertEquals(ExitCode.SUCCESS, runResult, example.getKey() + ": " + actualErr);
      assertEquals(
          example.getValue().replace("\n", System.lineSeparator()),
          actualOut.toString(),
          example.getKey());
      assertEquals("", actualErr.toString(), example.getKey());
    }
  }

  @Test
  void existingTargetPreventsAllWritesAndPreservesUserContent() throws IOException {
    Path existing = directory.resolve("todo.norm");
    Files.writeString(existing, "my own program");
    var out = new StringWriter();
    var err = new StringWriter();

    int result =
        new HelloCommand(directory).execute(List.of(), new PrintWriter(out), new PrintWriter(err));

    assertEquals(ExitCode.INPUT_ERROR, result);
    assertEquals("my own program", Files.readString(existing));
    assertFalse(Files.exists(directory.resolve("hell.norm")));
    assertFalse(Files.exists(directory.resolve("README.md")));
    assertTrue(err.toString().contains("todo.norm"));
    assertEquals("", out.toString());
  }

  @Test
  void rejectsArgumentsWithoutWritingFiles() {
    var out = new StringWriter();
    var err = new StringWriter();

    int result =
        new HelloCommand(directory)
            .execute(List.of("extra"), new PrintWriter(out), new PrintWriter(err));

    assertEquals(ExitCode.USAGE_ERROR, result);
    assertTrue(err.toString().contains("does not accept arguments"));
    assertEquals("", out.toString());
    assertFalse(Files.exists(directory.resolve("hell.norm")));
  }

  @Test
  void rollsBackPartiallyWrittenTemplateAfterAnIoFailure() throws IOException {
    var out = new StringWriter();
    var err = new StringWriter();
    HelloCommand.TemplateOpener opener =
        path -> {
          OutputStream output =
              Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
          if (!path.getFileName().toString().equals("maze.norm")) return output;
          return new FilterOutputStream(output) {
            @Override
            public void write(byte[] bytes) throws IOException {
              output.write(bytes[0]);
              throw new IOException("simulated interrupted write");
            }
          };
        };

    int result =
        new HelloCommand(directory, opener)
            .execute(List.of(), new PrintWriter(out), new PrintWriter(err));

    assertEquals(ExitCode.INPUT_ERROR, result);
    assertTrue(err.toString().contains("simulated interrupted write"));
    assertFalse(Files.exists(directory.resolve("hell.norm")));
    assertFalse(Files.exists(directory.resolve("sort.norm")));
    assertFalse(Files.exists(directory.resolve("maze.norm")));
    assertFalse(Files.exists(directory.resolve("README.md")));
  }
}
