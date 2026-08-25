package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import dev.w0fv1.norm.value.TypedProgram;
import java.io.PrintWriter;
import java.util.Objects;

public final class NormRuntime {
  private final ExecutionBackend backend;

  public NormRuntime() {
    this(new TruffleExecutionBackend());
  }

  public NormRuntime(ExecutionBackend backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  public void run(TypedProgram program, PrintWriter output) {
    Objects.requireNonNull(output, "output");
    run(program, ExecutionContext.of(output));
  }

  public void run(TypedProgram program, ExecutionContext context) {
    Objects.requireNonNull(program, "program");
    backend.execute(program.compilation().artifact(), Objects.requireNonNull(context, "context"));
  }
}
