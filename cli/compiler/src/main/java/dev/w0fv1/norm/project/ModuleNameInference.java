package dev.w0fv1.norm.project;

import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

final class ModuleNameInference {
  private ModuleNameInference() {}

  static Optional<String> infer(SourceFile moduleSource, Map<Path, SourceFile> overlays)
      throws IOException {
    Path modulePath = normalize(moduleSource.path());
    Path moduleRoot = modulePath.getParent();
    if (moduleRoot == null) throw new IOException("module configuration path has no parent");
    Map<Path, SourceFile> sources =
        ProjectLoader.collectSourceFiles(moduleRoot, moduleSource, overlays);
    String inferred = null;
    for (Map.Entry<Path, SourceFile> candidate : sources.entrySet()) {
      Optional<String> packageName = SourceHeader.parse(candidate.getValue()).packageName();
      if (packageName.isEmpty()) continue;
      Path relativeParent = moduleRoot.relativize(candidate.getKey()).getParent();
      int suffixSize = relativeParent == null ? 0 : relativeParent.getNameCount();
      String[] segments = packageName.orElseThrow().split("\\.");
      if (suffixSize >= segments.length || !matchesSuffix(segments, relativeParent, suffixSize)) {
        continue;
      }
      String value =
          String.join(".", java.util.Arrays.copyOf(segments, segments.length - suffixSize));
      if (inferred == null) inferred = value;
      else if (!inferred.equals(value)) {
        throw new IOException("module name cannot be inferred from inconsistent source packages");
      }
    }
    return Optional.ofNullable(inferred);
  }

  private static boolean matchesSuffix(String[] segments, Path suffix, int size) {
    for (int index = 0; index < size; index++) {
      if (!segments[segments.length - size + index].equals(suffix.getName(index).toString())) {
        return false;
      }
    }
    return true;
  }

  private static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }
}
