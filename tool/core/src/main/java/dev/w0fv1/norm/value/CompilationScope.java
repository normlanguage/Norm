package dev.w0fv1.norm.value;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CompilationScope(
    Map<DocumentId, ModuleSourceCoordinate> coordinates, ModuleGraph modules) {
  private static final ModuleCoordinate ANONYMOUS = new ModuleCoordinate("anonymous", 0);

  public CompilationScope {
    Map<DocumentId, ModuleSourceCoordinate> stable = new LinkedHashMap<>();
    Objects.requireNonNull(coordinates, "coordinates").entrySet().stream()
        .sorted(java.util.Comparator.comparing(entry -> entry.getKey().uri().toString()))
        .forEach(
            entry ->
                stable.put(
                    entry.getKey(), Objects.requireNonNull(entry.getValue(), "source coordinate")));
    Map<ModuleCoordinate, Set<String>> pathsByModule = new LinkedHashMap<>();
    for (ModuleSourceCoordinate coordinate : stable.values()) {
      if (!pathsByModule
          .computeIfAbsent(coordinate.module(), ignored -> new HashSet<>())
          .add(coordinate.relativePath())) {
        throw new IllegalArgumentException("source paths must be unique within a module");
      }
    }
    coordinates = java.util.Collections.unmodifiableMap(stable);
    Objects.requireNonNull(modules, "modules");
    if (!modules
        .modules()
        .containsAll(stable.values().stream().map(ModuleSourceCoordinate::module).toList())) {
      throw new IllegalArgumentException("source modules must belong to the module graph");
    }
  }

  public CompilationScope(Map<DocumentId, ModuleSourceCoordinate> coordinates) {
    this(
        coordinates,
        ModuleGraph.isolated(
            coordinates.values().stream().map(ModuleSourceCoordinate::module).toList()));
  }

  public static CompilationScope module(
      ModuleCoordinate module, Map<DocumentId, String> sourcePaths) {
    Objects.requireNonNull(module, "module");
    Map<DocumentId, ModuleSourceCoordinate> coordinates = new LinkedHashMap<>();
    Objects.requireNonNull(sourcePaths, "sourcePaths")
        .forEach(
            (document, relativePath) ->
                coordinates.put(document, new ModuleSourceCoordinate(module, relativePath)));
    return new CompilationScope(coordinates, ModuleGraph.isolated(List.of(module)));
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
    return module(ANONYMOUS, paths);
  }

  public String sourcePath(DocumentId document) {
    return coordinate(document).relativePath();
  }

  public ModuleSourceCoordinate coordinate(DocumentId document) {
    ModuleSourceCoordinate coordinate =
        coordinates.get(Objects.requireNonNull(document, "document"));
    if (coordinate == null)
      throw new IllegalArgumentException("source is outside the compilation scope");
    return coordinate;
  }

  public boolean canRead(DocumentId source, DocumentId target) {
    return modules.canRead(coordinate(source).module(), coordinate(target).module());
  }

  public boolean sameModule(DocumentId first, DocumentId second) {
    return coordinate(first).module().equals(coordinate(second).module());
  }

  public CompilationScope merge(CompilationScope other) {
    Objects.requireNonNull(other, "other");
    Map<DocumentId, ModuleSourceCoordinate> merged = new LinkedHashMap<>(coordinates);
    for (Map.Entry<DocumentId, ModuleSourceCoordinate> entry : other.coordinates.entrySet()) {
      ModuleSourceCoordinate previous = merged.putIfAbsent(entry.getKey(), entry.getValue());
      if (previous != null && !previous.equals(entry.getValue())) {
        throw new IllegalArgumentException("conflicting source in compilation scopes");
      }
    }
    return new CompilationScope(merged, modules.merge(other.modules));
  }

  public CompilationScope withReads(
      java.util.Collection<ModuleCoordinate> readers,
      java.util.Collection<ModuleCoordinate> targets) {
    return new CompilationScope(coordinates, modules.withReads(readers, targets));
  }
}
