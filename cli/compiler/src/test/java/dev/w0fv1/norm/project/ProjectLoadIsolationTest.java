package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.frontend.CompilationCancelledException;
import dev.w0fv1.norm.frontend.CompilationControl;
import dev.w0fv1.norm.frontend.CompilationLimits;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectLoadIsolationTest {
  @TempDir Path directory;

  @Test
  void capturesEachConcurrentLoadWithoutSharingInputOrEvaluationRecords() throws Exception {
    var runtime = new NormRuntime();
    var armed = new AtomicBoolean();
    var entered = new CountDownLatch(2);
    ExecutionBackend backend =
        (artifact, execution, context) -> {
          if (armed.get()) {
            entered.countDown();
            try {
              if (!entered.await(20, TimeUnit.SECONDS))
                throw new IllegalStateException("concurrent module evaluation did not start");
            } catch (InterruptedException exception) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(exception);
            }
          }
          runtime.execute(artifact, execution, context);
        };
    var environment = ProjectEnvironment.bootstrap(backend);
    Path left = module("left");
    Path right = module("right");
    armed.set(true);
    try (var loader = environment.projectLoader();
        var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> loader.load(left));
      var second = executor.submit(() -> loader.load(right));
      var leftResult = first.get(30, TimeUnit.SECONDS);
      var rightResult = second.get(30, TimeUnit.SECONDS);

      assertEquals(
          Set.of(left.getParent().resolve("module.norm")),
          leftResult.moduleEvaluations().stream()
              .map(value -> value.source().path())
              .collect(java.util.stream.Collectors.toSet()));
      assertEquals(
          Set.of(right.getParent().resolve("module.norm")),
          rightResult.moduleEvaluations().stream()
              .map(value -> value.source().path())
              .collect(java.util.stream.Collectors.toSet()));
      assertTrue(leftResult.inputs().files().stream().anyMatch(file -> file.path().equals(left)));
      assertFalse(leftResult.inputs().files().stream().anyMatch(file -> file.path().equals(right)));
      assertFalse(rightResult.inputs().files().stream().anyMatch(file -> file.path().equals(left)));
      assertThrows(
          UnsupportedOperationException.class, () -> leftResult.moduleEvaluations().clear());
    }
  }

  @Test
  void discoversConfigurationDirectoryWithoutExecutingModuleOrResolvingDependencies()
      throws Exception {
    var runtime = new NormRuntime();
    var executions = new AtomicInteger();
    ExecutionBackend backend =
        (artifact, execution, context) -> {
          executions.incrementAndGet();
          runtime.execute(artifact, execution, context);
        };
    var environment = ProjectEnvironment.bootstrap(backend);
    Path entry = module("app");
    Files.writeString(
        entry.getParent().resolve("module.norm"),
        "Module module() { module(name: \"app\", version: 1, dependencies: [dependency(repository: \"missing\", name: \"unavailable\")]) }\n");
    int baseline = executions.get();
    try (var loader = environment.projectLoader()) {
      assertEquals(entry.getParent(), loader.projectRoot(SourceFile.read(entry), List.of()));
      assertEquals(baseline, executions.get());
    }
  }

  @Test
  void cancelsBeforeEvaluatingModuleConfiguration() throws Exception {
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    Path entry = module("app");
    var control = new CompilationControl(() -> true, CompilationLimits.standard());
    try (var loader = environment.projectLoader()) {
      assertThrows(
          CompilationCancelledException.class,
          () -> loader.loadForAnalysis(SourceFile.read(entry), List.of(), control));
    }
  }

  @Test
  void returnsObservedInputsWhenAnInvalidBindingPreventsLoading() throws Exception {
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    Path entry = module("app");
    Path jar = entry.getParent().resolve("library.jar");
    Files.writeString(jar, "expected archive");
    String expected = dev.w0fv1.norm.value.Sha256Digest.compute(jar).value();
    Files.writeString(
        entry.getParent().resolve("module.norm"),
        "Module module() { module(name: \"app\", version: 1, binding: jarBinding(target: localJar(path: \"library.jar\", integrity: sha256(\""
            + expected
            + "\")), api: [])) }\n");
    Files.writeString(jar, "changed archive");
    try (var loader = environment.projectLoader()) {
      var failure = assertThrows(ProjectLoadException.class, () -> loader.loadForAnalysis(entry));
      assertTrue(failure.inputs().files().stream().anyMatch(file -> file.path().equals(jar)));
      assertTrue(failure.inputs().matches());
      Files.writeString(jar, "expected archive");
      assertFalse(failure.inputs().matches());
    }
  }

  private Path module(String name) throws Exception {
    Path root = Files.createDirectories(directory.resolve(name));
    Files.writeString(
        root.resolve("module.norm"),
        "Module module() { module(name: \"" + name + "\", version: 1, exports: [\"Main\"]) }\n");
    Path entry = root.resolve("Main.norm");
    Files.writeString(entry, "package " + name + "\nVoid main() {}\n");
    return entry;
  }

  @Test
  void capturesOverlayConfigurationOnceUsingItsEffectiveContent() throws Exception {
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    Path entry = module("app");
    Path configuration = entry.getParent().resolve("module.norm");
    var overlay =
        SourceFile.of(
            configuration,
            "Module module() { module(name: \"app\", version: 2, exports: [\"Main\"]) }\n");
    try (var loader = environment.projectLoader()) {
      var loaded = loader.load(SourceFile.read(entry), List.of(overlay));
      var captured =
          loaded.inputs().files().stream()
              .filter(file -> file.path().equals(configuration))
              .toList();
      assertEquals(1, captured.size());
      assertEquals(
          dev.w0fv1.norm.value.Sha256Digest.compute(
              overlay.text().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
          captured.getFirst().content());
    }
  }
}
