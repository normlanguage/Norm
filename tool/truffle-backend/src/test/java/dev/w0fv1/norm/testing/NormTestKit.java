package dev.w0fv1.norm.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.ProjectLoader;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.CompilationRequest;
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
    return new CompilerSession().compile(SourceFile.of(Path.of("test.norm"), text));
  }

  public static String run(String text) {
    return run(compile(text));
  }

  public static String run(Path path) throws Exception {
    return run(new CompilerSession().compile(new ProjectLoader().load(path).compilationRequest()));
  }

  private static String run(CompilationResult compilation) {
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    return run(compilation.program().orElseThrow());
  }

  private static String run(TypedProgram program) {
    StringWriter output = new StringWriter();
    new NormRuntime().run(program, new PrintWriter(output));
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
    List<Path> candidates;
    try (Stream<Path> files = Files.walk(directory)) {
      candidates =
          files
              .filter(Files::isRegularFile)
              .filter(path -> path.getFileName().toString().equals("module.norm"))
              .sorted()
              .toList();
    }
    List<Path> manifests = new ArrayList<>();
    for (Path candidate : candidates) {
      if (ProjectLoader.isManifest(SourceFile.read(candidate))) manifests.add(candidate);
    }
    List<DynamicTest> tests = new ArrayList<>();
    for (Path manifest : manifests) {
      List<Path> sourceCandidates;
      try (Stream<Path> files = Files.walk(manifest.getParent())) {
        sourceCandidates =
            files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".norm"))
                .filter(path -> !path.equals(manifest))
                .sorted()
                .toList();
      }
      List<Path> entryPoints = new ArrayList<>();
      for (Path source : sourceCandidates) {
        var sourceSet = new ProjectLoader().load(source);
        if (!sourceSet
            .manifestPath()
            .equals(java.util.Optional.of(manifest.toAbsolutePath().normalize()))) continue;
        CompilationRequest request = sourceSet.compilationRequest();
        var snapshot = new CompilerSession().snapshot(request);
        assertTrue(!snapshot.analysis().hasErrors(), () -> snapshot.diagnostics().toString());
        if (snapshot.analysis().entryPoint().isPresent()) entryPoints.add(source);
      }
      assertEquals(1, entryPoints.size(), manifest + " must contain exactly one entry point");
      Path entry = entryPoints.getFirst();
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
    CompilationResult compilation =
        new CompilerSession().compile(new ProjectLoader().load(path).compilationRequest());
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    StringWriter actual = new StringWriter();
    StringWriter expected = new StringWriter();
    new NormRuntime()
        .run(
            compilation.program().orElseThrow(),
            dev.w0fv1.norm.execution.ExecutionContext.testing(
                new PrintWriter(actual), new PrintWriter(expected)));
    assertTrue(!expected.toString().isEmpty(), path + " must declare expected output lines");
    assertEquals(expected.toString(), actual.toString(), () -> "unexpected output from " + path);
  }
}
