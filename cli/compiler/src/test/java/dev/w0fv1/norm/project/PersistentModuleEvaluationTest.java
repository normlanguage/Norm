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
  void consumesReplayedEvaluationOnceWithinTheCurrentInvocation() throws Exception {
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
      first.evaluate(source);
      evaluation = first.evaluations().getFirst();
    }
    try (var second = ModuleEvaluator.persistent(profile, backend)) {
      second.replay(java.util.List.of(evaluation));
      assertEquals(evaluation.declaration(), second.evaluate(source));
      assertEquals(1, executions.get());
      assertEquals(evaluation.declaration(), second.evaluate(source));
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
        assertEquals("sample", evaluator.evaluate(source).name().orElseThrow());
        var recorded = evaluator.evaluations().getFirst();
        var bytes = dev.w0fv1.norm.core.store.PortableObjectCodec.encode(recorded);
        var restored =
            dev.w0fv1.norm.core.store.PortableObjectCodec.decode(bytes, ModuleEvaluation.class);
        assertEquals(recorded.declaration(), restored.evaluate(runtime).declaration());
      }
    }
    assertEquals(2, executions.get());
  }
}
