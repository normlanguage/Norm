package dev.w0fv1.norm.project;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.jvm.LinkedJarBinding;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleDeclaration;
import java.util.Objects;

public record ModuleEvaluation(
    SourceFile source,
    CoreArtifact artifact,
    dev.w0fv1.norm.core.CoreExecutionPlan execution,
    ModuleDeclaration declaration,
    java.util.List<LinkedJarBinding> bindings) {
  public ModuleEvaluation {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(execution, "execution");
    Objects.requireNonNull(declaration, "declaration");
    bindings = java.util.List.copyOf(bindings);
  }

  public ModuleEvaluation evaluate(ExecutionBackend backend) throws java.io.IOException {
    return evaluate(source, artifact, execution, backend, bindings);
  }

  static ModuleEvaluation evaluate(
      SourceFile source,
      CoreArtifact artifact,
      dev.w0fv1.norm.core.CoreExecutionPlan execution,
      ExecutionBackend backend,
      java.util.List<LinkedJarBinding> bindings)
      throws java.io.IOException {
    var result = new java.util.concurrent.atomic.AtomicReference<ModuleDeclaration>();
    try (var runtime = JvmJarBindingRuntime.closedWorld(bindings)) {
      backend.execute(
          artifact,
          execution,
          ExecutionContext.module(
                  value -> {
                    if (!result.compareAndSet(null, value))
                      throw new IllegalStateException(
                          "module configuration produced more than one definition");
                  })
              .withJarBindingRuntime(runtime));
      if (result.get() == null)
        throw new IllegalStateException("module configuration did not produce a definition");
      return new ModuleEvaluation(source, artifact, execution, result.get(), bindings);
    } catch (IllegalArgumentException
        | IllegalStateException
        | dev.w0fv1.norm.execution.NormExecutionException exception) {
      throw new java.io.IOException(
          "invalid " + source.displayName() + ": " + exception.getMessage(), exception);
    }
  }
}
