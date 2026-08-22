package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import dev.w0fv1.norm.value.TypedProgram;
import java.io.PrintWriter;
import java.util.Objects;

public final class ProgramRunner {
  private final ExecutionBackend backend;

  public ProgramRunner() {
    this(new TruffleExecutionBackend());
  }

  public ProgramRunner(ExecutionBackend backend) {
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  public void run(TypedProgram program, PrintWriter output) {
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(output, "output");

    run(program, ExecutionContext.of(output));
  }

  public void run(TypedProgram program, ExecutionContext context) {
    Objects.requireNonNull(program, "program");
    backend.execute(program.boundProgram(), Objects.requireNonNull(context, "context"));
  }
}
