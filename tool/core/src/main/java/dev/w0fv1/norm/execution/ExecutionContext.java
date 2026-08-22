package dev.w0fv1.norm.execution;

import java.io.PrintWriter;
import java.io.Reader;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public record ExecutionContext(
    Reader input, PrintWriter output, List<String> arguments, BooleanSupplier cancellation) {
  public ExecutionContext {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(output, "output");
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(cancellation, "cancellation");
  }

  public static ExecutionContext of(PrintWriter output) {
    return new ExecutionContext(Reader.nullReader(), output, List.of(), () -> false);
  }
}
