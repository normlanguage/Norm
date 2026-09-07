package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.bridge.JavaDirectCall;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.PreparedExecution;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

record NativeApplicationProgram(
    PreparedExecution executable,
    JvmJarBindingRuntime.LinkedCalls calls,
    String packageName,
    dev.w0fv1.norm.jvm.LinkedJavaClasses classes,
    Map<String, JavaDirectCall> applicationCalls) {
  NativeApplicationProgram {
    Objects.requireNonNull(executable, "executable");
    Objects.requireNonNull(calls, "calls");
    Objects.requireNonNull(packageName, "packageName");
    Objects.requireNonNull(classes, "classes");
    applicationCalls = Map.copyOf(applicationCalls);
  }

  void execute(List<String> arguments, PrintWriter output, java.nio.file.Path directory) {
    try (var runtime = JvmJarBindingRuntime.closedWorld(calls, classes, applicationCalls)) {
      var context =
          ExecutionContext.builder()
              .output(output)
              .arguments(arguments)
              .platform(JdkSystemPlatform.standard())
              .applicationPackage(packageName)
              .applicationDirectory(directory)
              .jarBindingRuntime(runtime)
              .build();
      executable.execute(context);
    }
  }
}
