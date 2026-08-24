package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record ProjectSourceSet(
    Path root,
    Path entryPath,
    Optional<Path> manifestPath,
    CompilationScope scope,
    List<SourceFile> sources,
    Set<Path> exportedSourcePaths) {
  public ProjectSourceSet {
    root = normalize(Objects.requireNonNull(root, "root"));
    entryPath = normalize(Objects.requireNonNull(entryPath, "entryPath"));
    manifestPath =
        Objects.requireNonNull(manifestPath, "manifestPath").map(ProjectSourceSet::normalize);
    Objects.requireNonNull(scope, "scope");
    if (manifestPath.isPresent()
        && !manifestPath.orElseThrow().equals(root.resolve("module.norm"))) {
      throw new IllegalArgumentException("module manifest must be located at the project root");
    }
    Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(exportedSourcePaths, "exportedSourcePaths");

    Map<Path, SourceFile> sourcesByPath = new LinkedHashMap<>();
    for (SourceFile source : sources) {
      Path path = normalize(source.path());
      if (!path.startsWith(root)) {
        throw new IllegalArgumentException("project source is outside the project root: " + path);
      }
      if (sourcesByPath.putIfAbsent(path, source) != null) {
        throw new IllegalArgumentException("duplicate project source: " + path);
      }
    }
    if (!sourcesByPath.containsKey(entryPath)) {
      throw new IllegalArgumentException("entry source is not part of the project");
    }
    if (manifestPath.isEmpty() && sourcesByPath.size() != 1) {
      throw new IllegalArgumentException("standalone projects contain exactly one source");
    }
    sources =
        sourcesByPath.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Path::toString)))
            .map(Map.Entry::getValue)
            .toList();
    Set<DocumentId> sourceDocuments =
        sources.stream().map(SourceFile::id).collect(java.util.stream.Collectors.toSet());
    if (!scope.sourcePaths().keySet().equals(sourceDocuments)) {
      throw new IllegalArgumentException("project scope must describe every source document");
    }

    Set<Path> normalizedExports = new LinkedHashSet<>();
    for (Path exportedSource : exportedSourcePaths) {
      normalizedExports.add(normalize(exportedSource));
    }
    if (!sourcesByPath.keySet().containsAll(normalizedExports)) {
      throw new IllegalArgumentException("exported sources must be part of the project");
    }
    exportedSourcePaths = Set.copyOf(normalizedExports);
  }

  public SourceFile entrySource() {
    return sources.stream()
        .filter(source -> normalize(source.path()).equals(entryPath))
        .findFirst()
        .orElseThrow();
  }

  public CompilationRequest compilationRequest() {
    Set<DocumentId> exportedDocuments = new LinkedHashSet<>();
    for (SourceFile source : sources) {
      if (exportedSourcePaths.contains(normalize(source.path()))) {
        exportedDocuments.add(source.id());
      }
    }
    return new CompilationRequest(
        new CompilationUnitId(
            manifestPath.map(Path::toUri).orElseGet(() -> entrySource().id().uri())),
        scope,
        entrySource().id(),
        sources,
        exportedDocuments);
  }

  public Set<Path> inputPaths() {
    Set<Path> inputs = new LinkedHashSet<>();
    sources.stream().map(SourceFile::path).map(ProjectSourceSet::normalize).forEach(inputs::add);
    manifestPath.ifPresent(inputs::add);
    return Set.copyOf(inputs);
  }

  private static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }
}
