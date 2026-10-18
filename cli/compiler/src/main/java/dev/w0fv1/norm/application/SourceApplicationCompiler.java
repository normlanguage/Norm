package dev.w0fv1.norm.application;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.project.ModuleEvaluation;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

final class SourceApplicationCompiler
    implements LiveApplicationSession.CandidateCompiler, AutoCloseable {
  private final Path entry;
  private final ExecutionContext context;
  private final NormRuntime backend;
  private final Consumer<String> progress;
  private final PreparedApplicationCache cache;
  private List<ModuleEvaluation> replay;
  private ProjectEnvironment environment;
  private ApplicationRunner runner;
  private final boolean ownsRunner;
  private final dev.w0fv1.norm.jvm.JvmJarBindingRuntime.Realm realm =
      new dev.w0fv1.norm.jvm.JvmJarBindingRuntime.Realm();

  SourceApplicationCompiler(
      Path entry,
      ExecutionContext context,
      NormRuntime backend,
      Consumer<String> progress,
      PreparedApplicationCache cache,
      List<ModuleEvaluation> replay) {
    this.entry = entry;
    this.context = context;
    this.backend = backend;
    this.progress = progress;
    this.cache = cache;
    this.replay = replay;
    ownsRunner = true;
  }

  SourceApplicationCompiler(
      ApplicationRunner runner,
      Path entry,
      ExecutionContext context,
      Consumer<String> progress,
      PreparedApplicationCache cache,
      List<ModuleEvaluation> replay) {
    this.runner = runner;
    this.entry = entry;
    this.context = context;
    this.backend = null;
    this.progress = progress;
    this.cache = cache;
    this.replay = replay;
    ownsRunner = false;
  }

  @Override
  public ApplicationCandidate compile() throws IOException {
    if (runner == null) {
      progress.accept("Initializing compiler");
      environment = ProjectEnvironment.persistent(backend);
      runner = ApplicationRunner.persistent(environment, progress);
    }
    var candidate = runner.compileCandidate(entry, context, progress, cache, replay, realm);
    replay = List.of();
    return candidate;
  }

  dev.w0fv1.norm.jvm.JvmJarBindingRuntime.Realm realm() {
    return realm;
  }

  @Override
  public void close() throws IOException {
    try {
      if (ownsRunner && runner != null) runner.close();
    } finally {
      try {
        if (environment != null) environment.close();
      } finally {
        realm.close();
      }
    }
  }
}
