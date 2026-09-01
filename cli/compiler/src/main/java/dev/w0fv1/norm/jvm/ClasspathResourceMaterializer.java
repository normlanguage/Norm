package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.project.ModuleResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class ClasspathResourceMaterializer {
  public void materialize(Path classpathRoot, Map<String, ModuleResource> resources)
      throws IOException {
    Path root = Objects.requireNonNull(classpathRoot, "classpathRoot").toAbsolutePath().normalize();
    for (ModuleResource resource : Map.copyOf(resources).values()) {
      Path output = root.resolve(resource.path()).normalize();
      if (!output.startsWith(root)) {
        throw new IOException("module resource escapes the classpath root: " + resource.path());
      }
      Files.createDirectories(output.getParent());
      Files.write(output, resource.content());
    }
  }
}
