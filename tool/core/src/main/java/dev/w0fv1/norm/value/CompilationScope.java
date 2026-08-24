package dev.w0fv1.norm.value;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CompilationScope(ModuleCoordinate module, Map<DocumentId, String> sourcePaths) {
  private static final ModuleCoordinate ANONYMOUS = new ModuleCoordinate("anonymous", 0);

  public CompilationScope {
    Objects.requireNonNull(module, "module");
    Map<DocumentId, String> stable = new LinkedHashMap<>();
    Objects.requireNonNull(sourcePaths, "sourcePaths").entrySet().stream()
        .sorted(java.util.Comparator.comparing(entry -> entry.getKey().uri().toString()))
        .forEach(
            entry ->
                stable.put(
                    entry.getKey(),
                    new ModuleSourceCoordinate(module, entry.getValue()).relativePath()));
    if (new java.util.HashSet<>(stable.values()).size() != stable.size()) {
      throw new IllegalArgumentException("source paths must be unique within a module");
    }
    sourcePaths = java.util.Collections.unmodifiableMap(stable);
  }

  public static CompilationScope anonymous(List<SourceFile> sources) {
    List<SourceFile> ownedSources = List.copyOf(sources);
    if (ownedSources.isEmpty()) {
      throw new IllegalArgumentException("compilation scope requires source files");
    }
    Path commonRoot = null;
    for (SourceFile source : ownedSources) {
      Path path;
      try {
        path = source.path().toAbsolutePath().normalize();
      } catch (IllegalStateException exception) {
        commonRoot = null;
        break;
      }
      Path parent = path.getParent();
      if (parent == null) {
        commonRoot = null;
        break;
      }
      if (commonRoot == null) commonRoot = parent;
      while (commonRoot != null && !path.startsWith(commonRoot))
        commonRoot = commonRoot.getParent();
    }
    Map<DocumentId, String> paths = new LinkedHashMap<>();
    for (SourceFile source : ownedSources) {
      String logicalPath;
      if (commonRoot == null) {
        logicalPath = source.id().uri().toString();
      } else {
        logicalPath =
            commonRoot
                .relativize(source.path().toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
      }
      paths.put(source.id(), logicalPath);
    }
    return new CompilationScope(ANONYMOUS, paths);
  }

  public String sourcePath(DocumentId document) {
    String path = sourcePaths.get(Objects.requireNonNull(document, "document"));
    if (path == null) throw new IllegalArgumentException("source is outside the compilation scope");
    return path;
  }

  public ModuleSourceCoordinate coordinate(DocumentId document) {
    return new ModuleSourceCoordinate(module, sourcePath(document));
  }

  public Map<DocumentId, ModuleSourceCoordinate> coordinates() {
    Map<DocumentId, ModuleSourceCoordinate> result = new LinkedHashMap<>();
    sourcePaths.forEach(
        (document, relativePath) ->
            result.put(document, new ModuleSourceCoordinate(module, relativePath)));
    return java.util.Collections.unmodifiableMap(result);
  }
}
