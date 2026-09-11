package dev.w0fv1.norm.project;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleDescriptor;
import java.io.IOException;
import java.nio.file.Path;

final class ProjectPaths {
  private ProjectPaths() {}

  static String relativePath(Path root, Path path) {
    return root.relativize(path).toString().replace('\\', '/');
  }

  static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }

  static Path sourceRoot(SourceFile moduleSource, ModuleDescriptor descriptor) throws IOException {
    Path moduleRoot = normalize(moduleSource.path()).getParent();
    if (moduleRoot == null) throw new IOException("module configuration path has no parent");
    Path current = moduleRoot;
    String[] segments = descriptor.name().split("\\.");
    for (int index = segments.length - 1; index >= 0; index--) {
      Path name = current.getFileName();
      if (name == null || !name.toString().equals(segments[index])) {
        throw new IOException(
            "module configuration directory must match module name '"
                + descriptor.name()
                + "': "
                + moduleSource.path());
      }
      current = current.getParent();
      if (current == null) {
        throw new IOException("module configuration path has no source root");
      }
    }
    return normalize(current);
  }

  static Path repositoryRoot(Path root) {
    Path name = root.getFileName();
    if (name != null && name.toString().equals("dependencies") && root.getParent() != null) {
      return root.getParent();
    }
    return root;
  }
}
