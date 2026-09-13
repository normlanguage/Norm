package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record PreparedApplication(ApplicationProgramData program, List<String> classpath) {
  public static final String ENTRY = "application.bin";

  public PreparedApplication {
    Objects.requireNonNull(program, "program");
    classpath = List.copyOf(classpath);
  }

  public static PreparedApplication read(Path directory) throws IOException {
    return PortableObjectCodec.read(directory.resolve(ENTRY), PreparedApplication.class);
  }

  public void execute(Path directory, ExecutionContext context) {
    Path root = directory.toAbsolutePath().normalize();
    var paths =
        classpath.stream()
            .map(
                value -> {
                  Path path = root.resolve(value).normalize();
                  if (!path.startsWith(root))
                    throw new IllegalArgumentException(
                        "application classpath is outside its directory");
                  return path;
                })
            .toList();
    try (var runtime = JvmJarBindingRuntime.prepared(program.bindings(), paths)) {
      new NormRuntime()
          .execute(
              program.artifact(),
              program.execution(),
              context.withApplicationPackage(program.packageName()).withJarBindingRuntime(runtime));
    }
  }
}
