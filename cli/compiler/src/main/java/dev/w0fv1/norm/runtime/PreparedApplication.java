package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record PreparedApplication(
    ApplicationProgramData program, List<String> classpath, List<String> moduleRoots) {
  public static final String ENTRY = "application.bin";

  public PreparedApplication {
    Objects.requireNonNull(program, "program");
    classpath = List.copyOf(classpath);
    moduleRoots = List.copyOf(moduleRoots);
  }

  public static PreparedApplication read(Path directory) throws IOException {
    return PortableObjectCodec.read(directory.resolve(ENTRY), PreparedApplication.class);
  }

  public JvmJarBindingRuntime openRuntime(Path directory) {
    try (var assets = dev.w0fv1.norm.execution.RevisionResources.own(() -> {})) {
      return openRuntime(directory, assets);
    }
  }

  public JvmJarBindingRuntime openRuntime(
      Path directory, dev.w0fv1.norm.execution.RevisionResources.Lease assets) {
    return openRuntime(directory, assets, new JvmJarBindingRuntime.Realm());
  }

  public JvmJarBindingRuntime openRuntime(
      Path directory,
      dev.w0fv1.norm.execution.RevisionResources.Lease assets,
      JvmJarBindingRuntime.Realm realm) {
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
    return JvmJarBindingRuntime.prepared(program.bindings(), paths, moduleRoots, assets, realm);
  }

  public void execute(Path directory, ExecutionContext context) {
    try (var runtime = openRuntime(directory)) {
      new NormRuntime()
          .execute(
              program.artifact(),
              program.execution(),
              context.withApplicationPackage(program.packageName()).withJarBindingRuntime(runtime));
    }
  }
}
