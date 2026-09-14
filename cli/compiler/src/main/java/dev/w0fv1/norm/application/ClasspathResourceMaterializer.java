package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.store.ArtifactFileSet;
import dev.w0fv1.norm.project.ModuleResource;
import dev.w0fv1.norm.value.FileSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class ClasspathResourceMaterializer {
  public ArtifactFileSet materialize(Path classpathRoot, Map<String, ModuleResource> resources)
      throws IOException {
    Path root = Objects.requireNonNull(classpathRoot, "classpathRoot").toAbsolutePath().normalize();
    var files = new java.util.LinkedHashMap<Path, FileSnapshot>();
    for (ModuleResource resource : Map.copyOf(resources).values()) {
      Path output = root.resolve(resource.path()).normalize();
      if (!output.startsWith(root)) {
        throw new IOException("module resource escapes the classpath root: " + resource.path());
      }
      Files.createDirectories(output.getParent());
      Files.write(output, resource.content());
      files.put(root.relativize(output), FileSnapshot.capture(output));
    }
    return new ArtifactFileSet(files);
  }
}
