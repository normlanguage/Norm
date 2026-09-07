package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.execution.NormExecutionException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class NativeApplicationMain {
  private static NativeApplicationProgram application;

  private NativeApplicationMain() {}

  public static void main(String[] arguments) {
    PrintWriter output = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
    PrintWriter error = new PrintWriter(System.err, true, StandardCharsets.UTF_8);
    NativeApplicationProgram prepared = application();
    try {
      String executable = System.getenv("NORM_APPLICATION_EXECUTABLE");
      if (executable == null || executable.isBlank())
        executable = org.graalvm.nativeimage.ProcessProperties.getExecutableName();
      prepared.execute(
          List.of(arguments),
          output,
          java.nio.file.Path.of(executable).toAbsolutePath().normalize().getParent());
    } catch (NormExecutionException exception) {
      error.printf("error[%s]: %s%n", exception.code().id(), exception.getMessage());
      error.printf(" --> %s:%d:%d%n", exception.uri(), exception.line(), exception.column());
      renderCauses(exception, error);
      System.exit(1);
    }
  }

  private static void renderCauses(Throwable failure, PrintWriter error) {
    Throwable previous = failure;
    Throwable cause = failure.getCause();
    for (int depth = 0; cause != null && cause != previous && depth < 8; depth++) {
      String message = cause.getMessage();
      error.print("Caused by: " + cause.getClass().getName());
      if (message != null && !message.isBlank()) error.print(": " + message);
      error.println();
      if (message == null || message.isBlank()) {
        StackTraceElement[] trace = cause.getStackTrace();
        for (int index = 0; index < Math.min(trace.length, 12); index++) {
          error.println("  at " + trace[index]);
        }
      }
      previous = cause;
      cause = cause.getCause();
    }
  }

  static void install(
      NativeApplicationData value,
      dev.w0fv1.norm.execution.PreparedExecution prepared,
      java.util.Map<String, dev.w0fv1.norm.bridge.JavaDirectCall> calls,
      dev.w0fv1.norm.jvm.LinkedJavaClasses classes,
      java.util.Map<String, dev.w0fv1.norm.bridge.JavaDirectCall> applicationCalls) {
    if (application != null)
      throw new IllegalStateException("Native application is already installed");
    application =
        new NativeApplicationProgram(
            prepared,
            dev.w0fv1.norm.jvm.JvmJarBindingRuntime.prepareCalls(
                dev.w0fv1.norm.jvm.LinkedJarBinding.linkCalls(value.bindings()), calls),
            value.packageName(),
            classes,
            applicationCalls);
  }

  private static NativeApplicationProgram application() {
    if (application == null) {
      throw new IllegalStateException(
          "Native application was not installed during image generation");
    }
    return application;
  }
}
