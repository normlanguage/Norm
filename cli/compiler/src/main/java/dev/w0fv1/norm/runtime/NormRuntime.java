package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import java.io.PrintWriter;
import java.util.Objects;

public final class NormRuntime implements ExecutionBackend {
  private final ExecutionBackend backend;

  public NormRuntime() {
    this(new TruffleExecutionBackend());
  }

  public NormRuntime(ExecutionBackend backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  public void run(CoreArtifact program, PrintWriter output) {
    Objects.requireNonNull(output, "output");
    run(program, ExecutionContext.of(output, JdkSystemPlatform.standard()));
  }

  public void run(CoreArtifact program, ExecutionContext context) {
    Objects.requireNonNull(program, "program");
    execute(program, context);
  }

  @Override
  public void execute(
      CoreArtifact artifact, CoreExecutionPlan execution, ExecutionContext context) {
    backend.execute(
        Objects.requireNonNull(artifact, "artifact"),
        Objects.requireNonNull(execution, "execution"),
        Objects.requireNonNull(context, "context"));
  }
}
