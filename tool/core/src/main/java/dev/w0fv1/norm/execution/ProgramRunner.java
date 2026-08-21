package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.truffle.Language;
import dev.w0fv1.norm.value.TypedProgram;
import java.io.PrintWriter;
import java.util.Objects;

public final class ProgramRunner {
  public ProgramRunner() {}

  public void run(TypedProgram program, PrintWriter output) {
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(output, "output");

    Language.execute(program, output);
    output.flush();
  }
}
