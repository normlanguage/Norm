package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.PreparedExecution;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.jvm.LinkedJavaClasses;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

record NativeApplicationProgram(
    PreparedExecution executable,
    JvmJarBindingRuntime.LinkedCalls calls,
    String packageName,
    boolean windowed,
    LinkedJavaClasses classes,
    dev.w0fv1.norm.execution.JavaApplicationLinkage applicationCalls) {
  NativeApplicationProgram {
    Objects.requireNonNull(executable, "executable");
    Objects.requireNonNull(calls, "calls");
    Objects.requireNonNull(packageName, "packageName");
    Objects.requireNonNull(classes, "classes");
    Objects.requireNonNull(applicationCalls, "applicationCalls");
  }

  int execute(List<String> arguments, PrintWriter output, java.nio.file.Path directory) {
    try (var runtime = JvmJarBindingRuntime.closedWorld(calls, classes, applicationCalls)) {
      var environment = new java.util.HashMap<>(System.getenv());
      if (windowed) {
        var executable =
            java.nio.file.Path.of(
                    environment.getOrDefault(
                        "NORM_APPLICATION_EXECUTABLE",
                        org.graalvm.nativeimage.ProcessProperties.getExecutableName()))
                .getFileName()
                .toString();
        var name = executable.replaceFirst("(?i)\\.exe$", "");
        environment.put(
            "NORM_WINDOWED_LOG_DIRECTORY",
            java.nio.file.Path.of(environment.get("LOCALAPPDATA"), "Programs", "Norm", "logs", name)
                .toString());
      }
      var context =
          ExecutionContext.builder()
              .input(NativeStandardStreams.input())
              .error(NativeStandardStreams.error())
              .environment(environment)
              .output(output)
              .arguments(arguments)
              .platform(JdkSystemPlatform.standard())
              .applicationPackage(packageName)
              .applicationDirectory(directory)
              .jarBindingRuntime(runtime)
              .build();
      executable.execute(context);
      output.flush();
      context.error().flush();
      return context.exitCode();
    }
  }
}
