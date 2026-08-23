package dev.w0fv1.norm.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.frontend.ModuleManifestParser;
import dev.w0fv1.norm.frontend.ProjectLoader;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.TypedProgram;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public final class NormTestKit {
  private NormTestKit() {}

  public static CompilationResult compile(String text) {
    return new Compiler().compile(SourceFile.of(Path.of("test.norm"), text));
  }

  public static String run(String text) {
    return run(compile(text));
  }

  public static String run(Path path) throws Exception {
    return run(new Compiler().compile(new ProjectLoader().load(path)));
  }

  private static String run(CompilationResult compilation) {
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    return run(compilation.program().orElseThrow());
  }

  private static String run(TypedProgram program) {
    StringWriter output = new StringWriter();
    new ProgramRunner().run(program, new PrintWriter(output));
    return output.toString();
  }

  public static void assertOutput(String text, String... lines) {
    assertEquals(String.join(System.lineSeparator(), lines) + System.lineSeparator(), run(text));
  }

  public static Stream<DynamicTest> suite(String resource) throws Exception {
    Path directory = resourceDirectory(resource);
    List<Path> cases;
    try (Stream<Path> files = Files.list(directory)) {
      cases =
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().endsWith(".norm"))
              .sorted()
              .toList();
    }
    return cases.stream()
        .map(
            path ->
                DynamicTest.dynamicTest(
                    path.getFileName().toString(), () -> assertSelfContainedTest(path)));
  }

  public static Stream<DynamicTest> projectSuite(String resource) throws Exception {
    Path directory = resourceDirectory(resource);
    List<Path> manifests;
    try (Stream<Path> files = Files.walk(directory)) {
      manifests =
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().equals("module.norm"))
              .sorted()
              .toList();
    }
    List<DynamicTest> tests = new ArrayList<>();
    for (Path manifest : manifests) {
      new ModuleManifestParser().parse(SourceFile.read(manifest));
      List<Path> sources;
      try (Stream<Path> files = Files.walk(manifest.getParent())) {
        sources =
            files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".norm"))
                .filter(path -> !path.getFileName().toString().equals("module.norm"))
                .toList();
      }
      List<Path> entries = new ArrayList<>();
      for (Path source : sources) {
        if (new Compiler().analyze(SourceFile.read(source)).entryPoint().isPresent()) {
          entries.add(source);
        }
      }
      assertEquals(1, entries.size(), manifest + " must contain one entry point");
      Path entry = entries.getFirst();
      tests.add(
          DynamicTest.dynamicTest(
              directory.relativize(manifest.getParent()).toString(),
              () -> assertSelfContainedTest(entry)));
    }
    return tests.stream();
  }

  private static Path resourceDirectory(String resource) throws Exception {
    var url = Objects.requireNonNull(NormTestKit.class.getResource("/" + resource));
    return Path.of(url.toURI());
  }

  static void assertSelfContainedTest(Path path) throws Exception {
    CompilationResult compilation = new Compiler().compile(new ProjectLoader().load(path));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    StringWriter actual = new StringWriter();
    StringWriter expected = new StringWriter();
    new ProgramRunner()
        .run(
            compilation.program().orElseThrow(),
            dev.w0fv1.norm.execution.ExecutionContext.testing(
                new PrintWriter(actual), new PrintWriter(expected)));
    assertTrue(!expected.toString().isEmpty(), path + " must declare expected output lines");
    assertEquals(expected.toString(), actual.toString(), () -> "unexpected output from " + path);
  }
}
