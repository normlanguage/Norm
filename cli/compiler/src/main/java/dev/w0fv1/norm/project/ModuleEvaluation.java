package dev.w0fv1.norm.project;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleDeclaration;
import java.util.Objects;

public record ModuleEvaluation(
    SourceFile source,
    CoreArtifact artifact,
    dev.w0fv1.norm.core.CoreExecutionPlan execution,
    ModuleDeclaration declaration) {
  public ModuleEvaluation {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(execution, "execution");
    Objects.requireNonNull(declaration, "declaration");
  }

  public ModuleEvaluation evaluate(ExecutionBackend backend) throws java.io.IOException {
    return evaluate(source, artifact, execution, backend);
  }

  static ModuleEvaluation evaluate(
      SourceFile source,
      CoreArtifact artifact,
      dev.w0fv1.norm.core.CoreExecutionPlan execution,
      ExecutionBackend backend)
      throws java.io.IOException {
    var result = new java.util.concurrent.atomic.AtomicReference<ModuleDeclaration>();
    try {
      backend.execute(
          artifact,
          execution,
          ExecutionContext.module(
              value -> {
                if (!result.compareAndSet(null, value))
                  throw new IllegalStateException(
                      "module configuration produced more than one definition");
              }));
      if (result.get() == null)
        throw new IllegalStateException("module configuration did not produce a definition");
      return new ModuleEvaluation(source, artifact, execution, result.get());
    } catch (IllegalArgumentException
        | IllegalStateException
        | dev.w0fv1.norm.execution.NormExecutionException exception) {
      throw new java.io.IOException(
          "invalid " + source.displayName() + ": " + exception.getMessage(), exception);
    }
  }
}
