package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.frontend.LanguageProfile;
import dev.w0fv1.norm.frontend.ModuleBootstrap;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PersistentModuleEvaluationTest {
  @TempDir Path directory;

  @Test
  void resolvesLatestOncePerLoadAndRefreshesTheNextLoad() throws Exception {
    var profile = LanguageProfile.withPrelude(ModuleBootstrap.prelude());
    try (var evaluator = ModuleEvaluator.persistent(profile, new NormRuntime());
        var repository = new dev.w0fv1.norm.packages.LatestPackageRepositoryFixture(directory)) {
      var context =
          new ProjectLoadContext(
              evaluator,
              dev.w0fv1.norm.frontend.CompilationControl.standard(),
              java.util.List.of());
      var dependency =
          new dev.w0fv1.norm.value.ModuleDependency("github", "sample.library", null, false);
      assertEquals(1, context.resolve(dependency, repository.resolver()).version());
      repository.version(2);
      assertEquals(
          1,
          context
              .resolve(
                  new dev.w0fv1.norm.value.ModuleDependency("github", "sample.library", null, true),
                  repository.resolver())
              .version());
      assertEquals(1, repository.requests());
      var next =
          new ProjectLoadContext(
              evaluator,
              dev.w0fv1.norm.frontend.CompilationControl.standard(),
              java.util.List.of());
      assertEquals(2, next.resolve(dependency, repository.resolver()).version());
      assertEquals(2, repository.requests());
    }
  }

  @Test
  void reusesReplayedEvaluationWithinOneLoadAndExecutesAgainInTheNextLoad() throws Exception {
    var executions = new AtomicInteger();
    var runtime = new NormRuntime();
    ExecutionBackend backend =
        (artifact, execution, context) -> {
          executions.incrementAndGet();
          runtime.execute(artifact, execution, context);
        };
    var source =
        SourceFile.of(
            directory.resolve("module.norm"),
            "Module module() { module(name: \"sample\", version: 1) }");
    var profile = LanguageProfile.withPrelude(ModuleBootstrap.prelude());
    ModuleEvaluation evaluation;
    try (var first = ModuleEvaluator.persistent(profile, backend)) {
      evaluation = first.evaluate(source, dev.w0fv1.norm.frontend.CompilationControl.standard());
    }
    try (var second = ModuleEvaluator.persistent(profile, backend)) {
      var context =
          new ProjectLoadContext(
              second,
              dev.w0fv1.norm.frontend.CompilationControl.standard(),
              java.util.List.of(evaluation));
      assertEquals(evaluation.declaration(), context.evaluate(source));
      assertEquals(1, executions.get());
      assertEquals(evaluation.declaration(), context.evaluate(source));
      assertEquals(1, executions.get());
      var subsequent =
          new ProjectLoadContext(
              second, dev.w0fv1.norm.frontend.CompilationControl.standard(), java.util.List.of());
      assertEquals(evaluation.declaration(), subsequent.evaluate(source));
      assertEquals(2, executions.get());
    }
  }

  @Test
  void executesModuleFunctionOnEveryEvaluation() throws Exception {
    var executions = new AtomicInteger();
    var runtime = new NormRuntime();
    ExecutionBackend backend =
        (artifact, execution, context) -> {
          executions.incrementAndGet();
          runtime.execute(artifact, execution, context);
        };
    var profile = LanguageProfile.withPrelude(ModuleBootstrap.prelude());
    var source =
        SourceFile.of(
            directory.resolve("module.norm"),
            "Module module() { module(name: \"sample\", version: 1) }");
    for (int attempt = 0; attempt < 2; attempt++) {
      try (var evaluator = ModuleEvaluator.persistent(profile, backend)) {
        var recorded =
            evaluator.evaluate(source, dev.w0fv1.norm.frontend.CompilationControl.standard());
        assertEquals("sample", recorded.declaration().name().orElseThrow());
        var bytes = dev.w0fv1.norm.core.store.PortableObjectCodec.encode(recorded);
        var restored =
            dev.w0fv1.norm.core.store.PortableObjectCodec.decode(bytes, ModuleEvaluation.class);
        assertEquals(recorded.declaration(), restored.evaluate(runtime).declaration());
      }
    }
    assertEquals(2, executions.get());
  }
}
