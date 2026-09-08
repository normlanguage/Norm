package dev.w0fv1.norm.cli.value;

import dev.w0fv1.norm.application.ProjectTestReport;
import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticSeverity;
import dev.w0fv1.norm.execution.GuestStackFrame;
import dev.w0fv1.norm.execution.NormExecutionException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CommandReport(
    String command,
    Status status,
    List<Diagnostic> diagnostics,
    Optional<ProjectTestReport> tests,
    Optional<Failure> failure) {
  public CommandReport {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(status, "status");
    diagnostics = List.copyOf(diagnostics);
    Objects.requireNonNull(tests, "tests");
    Objects.requireNonNull(failure, "failure");
  }

  public static CommandReport checked(String command, List<Diagnostic> diagnostics) {
    boolean errors =
        diagnostics.stream()
            .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    return new CommandReport(
        command,
        errors ? Status.COMPILATION_ERROR : Status.SUCCESS,
        diagnostics,
        Optional.empty(),
        Optional.empty());
  }

  public static CommandReport tested(ProjectTestReport tests) {
    Status status =
        !tests.isSuccess()
            ? Status.TEST_FAILURE
            : tests.testsFound() == 0 ? Status.NO_TESTS : Status.SUCCESS;
    return new CommandReport("test", status, List.of(), Optional.of(tests), Optional.empty());
  }

  public static CommandReport failed(String command, Status status, String code, String message) {
    return new CommandReport(
        command,
        status,
        List.of(),
        Optional.empty(),
        Optional.of(new Failure(code, message, Optional.empty(), List.of())));
  }

  public static CommandReport runtimeFailure(String command, NormExecutionException exception) {
    return new CommandReport(
        command,
        Status.RUNTIME_ERROR,
        List.of(),
        Optional.empty(),
        Optional.of(
            new Failure(
                exception.code().id(),
                exception.getMessage(),
                Optional.of(
                    new GuestStackFrame(
                        command, exception.uri(), exception.line(), exception.column())),
                exception.guestStack())));
  }

  public enum Status {
    SUCCESS(ExitCode.SUCCESS),
    COMPILATION_ERROR(ExitCode.COMPILATION_ERROR),
    TEST_FAILURE(ExitCode.TEST_FAILURE),
    NO_TESTS(ExitCode.TEST_FAILURE),
    USAGE_ERROR(ExitCode.USAGE_ERROR),
    CONFLICT(ExitCode.INPUT_ERROR),
    INPUT_ERROR(ExitCode.INPUT_ERROR),
    RUNTIME_ERROR(ExitCode.RUNTIME_ERROR),
    INTERNAL_ERROR(ExitCode.INTERNAL_ERROR);

    private final int exitCode;

    Status(int exitCode) {
      this.exitCode = exitCode;
    }

    public int exitCode() {
      return exitCode;
    }
  }

  public record Failure(
      String code,
      String message,
      Optional<GuestStackFrame> location,
      List<GuestStackFrame> stack) {
    public Failure {
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(message, "message");
      Objects.requireNonNull(location, "location");
      stack = List.copyOf(stack);
    }
  }
}
