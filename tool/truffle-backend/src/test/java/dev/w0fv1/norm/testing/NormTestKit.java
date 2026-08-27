package dev.w0fv1.norm.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.SystemPlatform;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.project.ProjectLoader;
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
  private static final NormRuntime RUNTIME = new NormRuntime();
  private static final ProjectEnvironment ENVIRONMENT = environment();

  private NormTestKit() {}

  public static CompilationResult compile(String text) {
    try (CompilerSession compiler = ENVIRONMENT.compilerSession()) {
      return compiler.compile(SourceFile.of(Path.of("test.norm"), text));
    }
  }

  public static String run(String text) {
    return run(compile(text), JdkSystemPlatform.standard());
  }

  public static String run(String text, SystemPlatform platform) {
    return run(compile(text), platform);
  }

  public static String run(Path path) throws Exception {
    StringWriter output = new StringWriter();
    try (var launcher = ENVIRONMENT.launcher()) {
      CompilationResult result =
          launcher.run(
              path,
              dev.w0fv1.norm.execution.ExecutionContext.of(
                  new PrintWriter(output), JdkSystemPlatform.standard()));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    return output.toString();
  }

  private static String run(CompilationResult compilation, SystemPlatform platform) {
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    return run(compilation.program().orElseThrow(), platform);
  }

  private static String run(TypedProgram program, SystemPlatform platform) {
    StringWriter output = new StringWriter();
    RUNTIME.run(
        program, dev.w0fv1.norm.execution.ExecutionContext.of(new PrintWriter(output), platform));
    return output.toString();
  }

  public static void assertOutput(String text, String... lines) {
    assertEquals(String.join(System.lineSeparator(), lines) + System.lineSeparator(), run(text));
  }

  public static void assertOutput(SystemPlatform platform, String text, String... lines) {
    assertEquals(
        String.join(System.lineSeparator(), lines) + System.lineSeparator(), run(text, platform));
  }

  public static Stream<DynamicTest> suite(String resource) throws Exception {
    Path directory = resourceDirectory(resource);
    List<Path> cases;
    try (Stream<Path> files = Files.walk(directory)) {
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
                    directory.relativize(path).toString().replace('\\', '/'),
                    () -> assertSelfContainedTest(path)));
  }

  public static Stream<DynamicTest> projectSuite(String resource) throws Exception {
    Path directory = resourceDirectory(resource);
    List<Path> cases;
    try (Stream<Path> files = Files.list(directory)) {
      cases = files.filter(Files::isDirectory).sorted().toList();
    }
    List<DynamicTest> tests = new ArrayList<>();
    try (ProjectLoader projects = ENVIRONMENT.projectLoader();
        CompilerSession compiler = ENVIRONMENT.compilerSession()) {
      for (Path projectCase : cases) {
        Path module = projectCase.resolve("app").resolve("module.norm");
        assertTrue(Files.isRegularFile(module), projectCase + " must contain app/module.norm");
        assertTrue(
            ProjectLoader.isModuleSource(SourceFile.read(module)),
            module + " must declare the root module");
        List<Path> sourceCandidates;
        try (Stream<Path> files = Files.walk(module.getParent())) {
          sourceCandidates =
              files
                  .filter(Files::isRegularFile)
                  .filter(path -> path.getFileName().toString().endsWith(".norm"))
                  .filter(path -> !path.equals(module))
                  .sorted()
                  .toList();
        }
        List<Path> entryPoints = new ArrayList<>();
        for (Path source : sourceCandidates) {
          var sourceSet = projects.load(source);
          if (!sourceSet.modulePaths().contains(module.toAbsolutePath().normalize())) continue;
          CompilationRequest request = sourceSet.compilationRequest();
          var snapshot = compiler.snapshot(request);
          assertTrue(
              !snapshot.analysis().hasErrors(),
              () -> projectCase.getFileName() + ": " + snapshot.diagnostics());
          if (snapshot.analysis().entryPoint().isPresent()) entryPoints.add(source);
        }
        assertEquals(1, entryPoints.size(), module + " must contain exactly one entry point");
        Path entry = entryPoints.getFirst();
        tests.add(
            DynamicTest.dynamicTest(
                projectCase.getFileName().toString(), () -> assertSelfContainedTest(entry)));
      }
    }
    return tests.stream();
  }

  private static Path resourceDirectory(String resource) throws Exception {
    var url = Objects.requireNonNull(NormTestKit.class.getResource("/" + resource));
    return Path.of(url.toURI());
  }

  static void assertSelfContainedTest(Path path) throws Exception {
    StringWriter actual = new StringWriter();
    StringWriter expected = new StringWriter();
    try (var launcher = ENVIRONMENT.launcher()) {
      CompilationResult compilation =
          launcher.run(
              path,
              dev.w0fv1.norm.execution.ExecutionContext.testing(
                  new PrintWriter(actual),
                  new PrintWriter(expected),
                  JdkSystemPlatform.standard()));
      assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    }
    assertTrue(!expected.toString().isEmpty(), path + " must declare expected output lines");
    assertEquals(expected.toString(), actual.toString(), () -> "unexpected output from " + path);
  }

  private static ProjectEnvironment environment() {
    try {
      return ProjectEnvironment.bootstrap(RUNTIME);
    } catch (java.io.IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
