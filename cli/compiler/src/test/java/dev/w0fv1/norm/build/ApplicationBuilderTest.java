package dev.w0fv1.norm.build;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.application.TemporaryDirectory;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
final class ApplicationBuilderTest {
  private static ProjectEnvironment environment;
  @TempDir Path directory;
  private ApplicationRunner runner;
  private ApplicationBuilder builder;
  private Path launcher;
  private final String resourceName = "owner-" + java.util.UUID.randomUUID() + ".txt";
  private final Set<Path> ownedWorkspaces = new java.util.HashSet<>();

  @BeforeAll
  static void createEnvironment() throws Exception {
    environment = ProjectEnvironment.bootstrap(new NormRuntime());
  }

  @BeforeEach
  void openRunner() throws Exception {
    runner = ApplicationRunner.open(environment);
    launcher = Files.write(directory.resolve("launcher.exe"), new byte[] {'M', 'Z', 1, 2});
    builder = new ApplicationBuilder(runner, Optional.of(launcher));
    markResources(directory);
  }

  @AfterEach
  void closeRunner() {
    runner.close();
    for (Path workspace : ownedWorkspaces)
      assertFalse(Files.exists(workspace), workspace.toString());
  }

  @Test
  void buildsCapturedJvmBundleWithoutCliAndCompilesOnlyOnce() throws Exception {
    Path source = writeSource("hello.norm", "Void main() { printLine(\"captured\") }");
    var events = new ArrayList<BuildProgress>();
    Set<Path> before = temporaryWorkspaces();
    BuildResult result =
        build(
            new BuildRequest(
                source,
                ApplicationBuildTarget.JVM,
                false,
                dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
            event -> {
              events.add(event);
              if (event.stage() == BuildProgress.Stage.JVM_PACKAGING) {
                try {
                  Files.writeString(source, "Void main() { printLine(\"changed\") }");
                } catch (IOException failure) {
                  throw new java.io.UncheckedIOException(failure);
                }
              }
            });
    Path output = assertInstanceOf(BuildResult.Success.class, result).output();
    assertEquals(source.resolveSibling("hello.norm.exe"), output);
    assertTrue(Files.isRegularFile(output));
    assertEquals(
        1, events.stream().filter(e -> e.message().equals("Compiling Norm sources")).count());
    assertEquals(
        1, events.stream().filter(e -> e.message().equals("Processing Java annotations")).count());
    assertEquals(BuildProgress.Stage.COMPLETE, events.getLast().stage());
    assertEquals(before, temporaryWorkspaces());

    byte[] executable = Files.readAllBytes(output);
    byte[] magic = "NORMAPP1".getBytes(StandardCharsets.US_ASCII);
    assertArrayEquals(Files.readAllBytes(launcher), Arrays.copyOf(executable, 4));
    assertArrayEquals(
        magic, Arrays.copyOfRange(executable, executable.length - 8, executable.length));
    int trailer = executable.length - 48;
    long length = ByteBuffer.wrap(executable, trailer, Long.BYTES).getLong();
    assertEquals(trailer - 4, length);
    byte[] bundle = Arrays.copyOfRange(executable, 4, trailer);
    assertArrayEquals(
        MessageDigest.getInstance("SHA-256").digest(bundle),
        Arrays.copyOfRange(executable, trailer + 8, trailer + 40));
    Path extracted = Files.createDirectories(directory.resolve("extracted"));
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bundle))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        Path target = extracted.resolve(entry.getName()).normalize();
        assertTrue(target.startsWith(extracted));
        Files.createDirectories(target.getParent());
        Files.copy(zip, target);
      }
    }
    var descriptor =
        com.google.gson.JsonParser.parseString(
                Files.readString(extracted.resolve("application.json")))
            .getAsJsonObject();
    assertEquals(2, descriptor.get("formatVersion").getAsInt());
    assertEquals("application.bin", descriptor.get("entry").getAsString());
    var text = new StringWriter();
    dev.w0fv1.norm.runtime.PreparedApplication.read(extracted)
        .execute(extracted, ExecutionContext.of(new PrintWriter(text)));
    assertEquals("captured" + System.lineSeparator(), text.toString());
    assertTrue(runner.compile(source).isSuccess());
  }

  @Test
  void discoversProjectEntryAndKeepsOutputLayout() throws Exception {
    Path project = Files.createDirectories(directory.resolve("hello/web"));
    markResources(project);
    Files.writeString(
        project.resolve("module.norm"),
        "Module module() { return module(name: \"hello.web\", version: 1) }");
    Files.writeString(project.resolve("application.norm"), "package hello.web Void main() {}");
    BuildResult result =
        build(
            new BuildRequest(
                project,
                ApplicationBuildTarget.JVM,
                false,
                dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
            e -> {});
    assertEquals(
        project.resolve("build/web.exe"),
        assertInstanceOf(BuildResult.Success.class, result).output());
  }

  @Test
  void returnsUnchangedCompilationDiagnosticsWithoutPublishing() throws Exception {
    Path source = writeSource("invalid.norm", "Void main() { missing() }");
    var expected = runner.compile(source);
    var events = new ArrayList<BuildProgress>();
    var result =
        assertInstanceOf(
            BuildResult.CompilationFailure.class,
            build(
                new BuildRequest(
                    source,
                    ApplicationBuildTarget.JVM,
                    false,
                    dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
                events::add));
    assertEquals(expected.diagnostics(), result.diagnostics());
    assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().clear());
    assertFalse(Files.exists(directory.resolve("invalid.norm.exe")));
    assertTrue(events.stream().noneMatch(e -> e.stage() == BuildProgress.Stage.COMPLETE));
  }

  @Test
  void leavesBorrowedRunnerAndPreviouslyCompiledApplicationOpen() throws Exception {
    Path source = writeSource("owned.norm", "Void main() {}");
    try (var held = runner.compileApplication(source)) {
      var app = held.application().orElseThrow();
      Path classes = app.annotations().classes();
      Files.createDirectories(classes);
      Path resource = Files.writeString(classes.resolve("held.txt"), "held");
      Set<Path> before = temporaryWorkspaces();
      assertInstanceOf(
          BuildResult.Success.class,
          build(
              new BuildRequest(
                  source,
                  ApplicationBuildTarget.JVM,
                  false,
                  dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
              e -> {}));
      assertEquals(before, temporaryWorkspaces());
      assertEquals("held", Files.readString(resource));
      try (var runtime = app.openRuntime()) {
        assertNotNull(runtime);
      }
      assertTrue(runner.compile(source).isSuccess());
    }
  }

  @Test
  void cleansOwnedResourcesOnPublicationFailureWithoutReportingSuccess() throws Exception {
    Path source = writeSource("blocked.norm", "Void main() {}");
    Path output = Files.createDirectories(directory.resolve("blocked.norm.exe"));
    Path existing = Files.writeString(output.resolve("keep.txt"), "keep");
    Set<Path> before = temporaryWorkspaces();
    var events = new ArrayList<BuildProgress>();
    assertThrows(
        IOException.class,
        () ->
            build(
                new BuildRequest(
                    source,
                    ApplicationBuildTarget.JVM,
                    false,
                    dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
                events::add));
    assertEquals("keep", Files.readString(existing));
    assertEquals(before, temporaryWorkspaces());
    assertTrue(events.stream().noneMatch(e -> e.stage() == BuildProgress.Stage.COMPLETE));
    assertTrue(runner.compile(source).isSuccess());
  }

  @Test
  void preservesProgressFailureAndCleansTheCompiledApplication() throws Exception {
    Path source = writeSource("cancelled.norm", "Void main() {}");
    var failure =
        new IllegalStateException("progress interrupted", new IOException("transport lost"));
    Set<Path> before = temporaryWorkspaces();
    var actual =
        assertThrows(
            IllegalStateException.class,
            () ->
                build(
                    new BuildRequest(
                        source,
                        ApplicationBuildTarget.JVM,
                        false,
                        dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
                    event -> {
                      if (event.stage() == BuildProgress.Stage.JVM_PACKAGING) throw failure;
                    }));
    assertSame(failure, actual);
    assertSame(failure.getCause(), actual.getCause());
    assertEquals(before, temporaryWorkspaces());
    assertFalse(Files.exists(directory.resolve("cancelled.norm.exe")));
    assertTrue(runner.compile(source).isSuccess());
  }

  @Test
  void rejectsUnavailableLauncherBeforeCompiling() throws Exception {
    Path source = writeSource("unavailable.norm", "Void main() {}");
    var events = new ArrayList<BuildProgress>();
    Files.delete(launcher);
    assertThrows(
        IOException.class,
        () ->
            build(
                new BuildRequest(
                    source,
                    ApplicationBuildTarget.JVM,
                    false,
                    dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
                events::add));
    assertTrue(events.isEmpty());
    var unconfigured = new ApplicationBuilder(runner, Optional.empty());
    assertThrows(
        IOException.class,
        () ->
            unconfigured.build(
                new BuildRequest(
                    source,
                    ApplicationBuildTarget.JVM,
                    false,
                    dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
                events::add));
    assertTrue(events.isEmpty());
  }

  @Test
  void preservesPrimaryFailureWhenApplicationCleanupAlsoFails() throws Exception {
    Path source = writeSource("locked.norm", "Void main() {}");
    Set<Path> before = temporaryWorkspaces();
    var owned = new java.util.HashSet<Path>();
    var channel = new java.util.concurrent.atomic.AtomicReference<java.io.RandomAccessFile>();
    var failure = new IllegalStateException("build interrupted");
    try {
      var actual =
          assertThrows(
              IllegalStateException.class,
              () ->
                  build(
                      new BuildRequest(
                          source,
                          ApplicationBuildTarget.JVM,
                          false,
                          dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
                      event -> {
                        if (event.stage() != BuildProgress.Stage.JVM_PACKAGING) return;
                        try {
                          owned.addAll(temporaryWorkspaces());
                          owned.removeAll(before);
                          assertEquals(1, owned.size());
                          Path locked =
                              Files.writeString(
                                  owned.iterator().next().resolve("locked.txt"), "locked");
                          channel.set(new java.io.RandomAccessFile(locked.toFile(), "r"));
                        } catch (IOException exception) {
                          throw new java.io.UncheckedIOException(exception);
                        }
                        throw failure;
                      }));
      assertSame(failure, actual);
      assertEquals(1, actual.getSuppressed().length);
      assertInstanceOf(java.io.UncheckedIOException.class, actual.getSuppressed()[0]);
      assertFalse(Files.exists(directory.resolve("locked.norm.exe")));
    } finally {
      if (channel.get() != null) channel.get().close();
      for (Path workspace : owned) {
        if (!Files.exists(workspace)) continue;
        try (var paths = Files.walk(workspace)) {
          for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
            Files.deleteIfExists(path);
        }
      }
    }
    assertEquals(before, temporaryWorkspaces());
  }

  @Test
  void ignoresOtherBuildLifecyclesWhenCheckingOwnedWorkspaceCleanup() throws Exception {
    Path source = writeSource("independent.norm", "Void main() {}");
    var other = new java.util.concurrent.atomic.AtomicReference<TemporaryDirectory>();
    var existing = new TemporaryDirectory();
    try {
      Path unrelated = existing.path();
      var result =
          build(
              new BuildRequest(
                  source,
                  ApplicationBuildTarget.JVM,
                  false,
                  dev.w0fv1.norm.build.WindowsSubsystem.CONSOLE),
              event -> {
                if (event.stage() != BuildProgress.Stage.JVM_PACKAGING) return;
                existing.close();
                other.set(new TemporaryDirectory());
              });
      assertInstanceOf(BuildResult.Success.class, result);
      assertFalse(Files.exists(unrelated));
      assertNotNull(other.get());
      assertTrue(Files.isDirectory(other.get().path()));
      assertTrue(temporaryWorkspaces().isEmpty());
    } finally {
      existing.close();
      if (other.get() != null) other.get().close();
    }
  }

  private BuildResult build(
      BuildRequest request, java.util.function.Consumer<BuildProgress> progress)
      throws IOException {
    Set<Path> borrowed = temporaryWorkspaces();
    return builder.build(
        request,
        event -> {
          if (event.stage() == BuildProgress.Stage.JVM_PACKAGING) {
            try {
              Set<Path> current = temporaryWorkspaces();
              current.removeAll(borrowed);
              assertEquals(
                  1, current.size(), "this build's captured resource must identify its workspace");
              ownedWorkspaces.addAll(current);
            } catch (IOException exception) {
              throw new java.io.UncheckedIOException(exception);
            }
          }
          progress.accept(event);
        });
  }

  private Path writeSource(String name, String source) throws IOException {
    return Files.writeString(
        directory.resolve(name), "Module module() { return module(dependencies: []) } " + source);
  }

  private void markResources(Path root) throws IOException {
    Path resources = Files.createDirectories(root.resolve("resources"));
    Files.writeString(resources.resolve(resourceName), "owned by this test");
  }

  private Set<Path> temporaryWorkspaces() throws IOException {
    try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
      return paths
          .filter(Files::isDirectory)
          .filter(p -> p.getFileName().toString().startsWith("norm-build-"))
          .filter(
              p -> Files.isRegularFile(p.resolve("build/norm/java/classes").resolve(resourceName)))
          .collect(Collectors.toSet());
    }
  }
}
