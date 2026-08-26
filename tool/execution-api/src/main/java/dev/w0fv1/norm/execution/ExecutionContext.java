package dev.w0fv1.norm.execution;

import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public record ExecutionContext(
    Reader input,
    PrintWriter output,
    PrintWriter expectedOutput,
    List<String> arguments,
    BooleanSupplier cancellation,
    Optional<ModulePublisher> modulePublisher) {
  public ExecutionContext {
    Objects.requireNonNull(input, "input");
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(expectedOutput, "expectedOutput");
    arguments = List.copyOf(arguments);
    Objects.requireNonNull(cancellation, "cancellation");
    modulePublisher = Objects.requireNonNull(modulePublisher, "modulePublisher");
  }

  public ExecutionContext(
      Reader input, PrintWriter output, List<String> arguments, BooleanSupplier cancellation) {
    this(
        input,
        output,
        new PrintWriter(Writer.nullWriter()),
        arguments,
        cancellation,
        Optional.empty());
  }

  public ExecutionContext(
      Reader input,
      PrintWriter output,
      PrintWriter expectedOutput,
      List<String> arguments,
      BooleanSupplier cancellation) {
    this(input, output, expectedOutput, arguments, cancellation, Optional.empty());
  }

  public static ExecutionContext of(PrintWriter output) {
    return new ExecutionContext(Reader.nullReader(), output, List.of(), () -> false);
  }

  public static ExecutionContext testing(PrintWriter output, PrintWriter expectedOutput) {
    return new ExecutionContext(
        Reader.nullReader(), output, expectedOutput, List.of(), () -> false);
  }

  public static ExecutionContext module(ModulePublisher publisher) {
    return new ExecutionContext(
        Reader.nullReader(),
        new PrintWriter(Writer.nullWriter()),
        new PrintWriter(Writer.nullWriter()),
        List.of(),
        () -> false,
        Optional.of(publisher));
  }
}
