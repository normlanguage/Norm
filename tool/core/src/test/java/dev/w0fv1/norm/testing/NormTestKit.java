package dev.w0fv1.norm.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.frontend.ProjectLoader;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.TypedProgram;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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

  private static Path resourceDirectory(String resource) throws Exception {
    var url = Objects.requireNonNull(NormTestKit.class.getResource("/" + resource));
    return Path.of(url.toURI());
  }

  static void assertSelfContainedTest(Path path) throws Exception {
    CompilationResult compilation = new Compiler().compile(new ProjectLoader().load(path));
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    TypedProgram main = compilation.program().orElseThrow();
    var expectedOutput =
        main.boundProgram().callables().stream()
            .filter(function -> function.name().equals("expectedOutput"))
            .findFirst()
            .orElseThrow(() -> new AssertionError(path + " must define expectedOutput()"));
    assertTrue(
        expectedOutput.parameters().isEmpty(), path + " expectedOutput() must have no parameters");
    assertEquals(
        "void", expectedOutput.returnType().name(), path + " expectedOutput() must return void");
    assertEquals(
        dev.w0fv1.norm.bound.BoundVisibility.PRIVATE,
        expectedOutput.visibility(),
        path + " expectedOutput() must be private");
    assertEquals(
        run(new TypedProgram(main.boundProgram().withEntryPoint(expectedOutput.id()))),
        run(main),
        () -> "unexpected output from " + path);
  }
}
