package dev.w0fv1.norm.application;

import dev.w0fv1.norm.runtime.PreparedApplicationContent;
import dev.w0fv1.norm.value.FileSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class PreparedApplicationWriter {
  public Path write(CompiledApplication application, Path directory) throws IOException {
    return capture(application).materialize(directory);
  }

  public PreparedApplicationContent capture(CompiledApplication application) throws IOException {
    var classes = new LinkedHashMap<String, byte[]>();
    Path source = application.annotations().classes();
    if (Files.isDirectory(source)) {
      try (var files = Files.walk(source)) {
        for (Path file : files.filter(Files::isRegularFile).sorted().toList())
          classes.put(
              source.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
      }
    }
    var program = ApplicationProgramPlan.from(application).data();
    var dependencies =
        application.javaClasspath().artifacts().stream()
            .map(artifact -> new FileSnapshot(artifact.file(), artifact.content()))
            .toList();
    var moduleRoots =
        dev.w0fv1.norm.jvm.JavaModulePath.inspect(application.javaClasspath().rootPaths()).names();
    return new PreparedApplicationContent(program, classes, dependencies, moduleRoots);
  }
}
