package dev.w0fv1.norm.application;

import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.LiveExecution;
import dev.w0fv1.norm.jvm.LinkedJarBinding;
import dev.w0fv1.norm.project.ProjectInputSnapshot;
import dev.w0fv1.norm.project.ProjectLoadException;
import dev.w0fv1.norm.project.ProjectLoadResult;
import dev.w0fv1.norm.project.ProjectLoader;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class LiveApplicationSession implements AutoCloseable {
  private final ProjectLoader projects;
  private final ApplicationCompiler compiler;
  private final Path entry;
  private final CompiledApplication initial;
  private final LiveExecution execution;
  private final Consumer<String> diagnostics;
  private final Consumer<String> progress;
  private final InputChangeMonitor monitor;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final Map<Path, Sha256Digest> classes;
  private ProjectInputSnapshot inputs;
  private Thread worker;

  LiveApplicationSession(
      ProjectLoader projects,
      ApplicationCompiler compiler,
      Path entry,
      ProjectInputSnapshot inputs,
      CompiledApplication initial,
      LiveExecution execution,
      Consumer<String> progress,
      Consumer<String> diagnostics)
      throws IOException {
    this.projects = projects;
    this.compiler = compiler;
    this.entry = entry;
    this.inputs = inputs;
    this.initial = initial;
    this.execution = execution;
    this.progress = progress;
    this.diagnostics = diagnostics;
    classes = classContents(initial.annotations().classes());
    monitor = new InputChangeMonitor();
    try {
      monitor.update(inputs);
    } catch (IOException | RuntimeException exception) {
      monitor.close();
      throw exception;
    }
  }

  void start() {
    worker =
        Thread.ofVirtual()
            .name("norm-live-compilation")
            .start(
                () -> {
                  boolean changed = !inputs.matches();
                  while (!closed.get()) {
                    try {
                      if (!changed && !monitor.awaitChange(Duration.ofMillis(100))) continue;
                      monitor.update(inputs);
                      if (inputs.matches()) {
                        changed = false;
                        continue;
                      }
                      changed = reload();
                    } catch (InterruptedException exception) {
                      Thread.currentThread().interrupt();
                      return;
                    } catch (java.nio.file.ClosedWatchServiceException exception) {
                      return;
                    } catch (IOException | RuntimeException exception) {
                      if (!closed.get())
                        diagnostics.accept("error[NORM-LIVE-0001]: " + exception.getMessage());
                      changed = false;
                    }
                  }
                });
  }

  private boolean reload() throws IOException {
    ProjectLoadResult loaded;
    try {
      loaded = projects.load(entry);
    } catch (ProjectLoadException exception) {
      inputs = exception.inputs();
      monitor.update(inputs);
      diagnostics.accept("error[NORM-LIVE-0001]: " + exception.getMessage());
      return false;
    }
    inputs = loaded.inputs();
    monitor.update(inputs);
    var sources = loaded.sources();
    try (var compilation =
        compiler.compile(
            new ApplicationInput(
                sources.applicationCompilationRequest(entry), java.util.Optional.of(sources)),
            List.of(),
            progress)) {
      if (closed.get()) return false;
      if (!inputs.matches()) return true;
      if (!compilation.result().isSuccess()) {
        compilation
            .result()
            .diagnostics()
            .forEach(value -> diagnostics.accept(DiagnosticRenderer.render(value)));
        return false;
      }
      var candidate = compilation.application().orElseThrow();
      if (!initial.javaBindings().stream()
              .map(binding -> binding.graph().contentId())
              .toList()
              .equals(
                  candidate.javaBindings().stream()
                      .map(binding -> binding.graph().contentId())
                      .toList())
          || !initial.javaBindings().stream()
              .map(LinkedJarBinding::from)
              .toList()
              .equals(candidate.javaBindings().stream().map(LinkedJarBinding::from).toList())
          || !initial.sourceSet().resourceSet().equals(candidate.sourceSet().resourceSet())
          || !classes.equals(classContents(candidate.annotations().classes()))) {
        diagnostics.accept(
            "error[NORM-LIVE-0002]: Java dependencies, generated classes or resources changed; restart the application");
        return false;
      }
      if (!inputs.matches()) return true;
      if (closed.get()) return false;
      try {
        execution.update(compilation.result().output().orElseThrow().artifact());
        progress.accept("Updated running application");
      } catch (IllegalArgumentException exception) {
        diagnostics.accept("error[NORM-LIVE-0002]: " + exception.getMessage());
      }
      return false;
    }
  }

  static Map<Path, Sha256Digest> classContents(Path root) throws IOException {
    var content = new LinkedHashMap<Path, Sha256Digest>();
    if (Files.isDirectory(root)) {
      try (var files = Files.walk(root)) {
        for (Path file : files.filter(Files::isRegularFile).sorted().toList())
          content.put(root.relativize(file), Sha256Digest.compute(file));
      }
    }
    return Map.copyOf(content);
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) return;
    try {
      monitor.close();
    } finally {
      if (worker != null) {
        boolean interrupted = Thread.interrupted();
        try {
          worker.interrupt();
          while (worker.isAlive()) {
            try {
              worker.join();
            } catch (InterruptedException exception) {
              interrupted = true;
            }
          }
        } finally {
          if (interrupted) Thread.currentThread().interrupt();
        }
      }
    }
  }
}
