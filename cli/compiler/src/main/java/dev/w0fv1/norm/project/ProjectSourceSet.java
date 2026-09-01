package dev.w0fv1.norm.project;

import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
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
    Path primaryPath,
    Optional<Path> rootModulePath,
    Set<Path> modulePaths,
    CompilationScope scope,
    List<SourceFile> sources,
    Set<Path> exportedSourcePaths,
    Set<DocumentId> bindingSourceDocuments,
    List<ResolvedJarBinding> jarBindings) {
  public ProjectSourceSet {
    root = normalize(Objects.requireNonNull(root, "root"));
    primaryPath = normalize(Objects.requireNonNull(primaryPath, "primaryPath"));
    rootModulePath =
        Objects.requireNonNull(rootModulePath, "rootModulePath").map(ProjectSourceSet::normalize);
    modulePaths =
        Objects.requireNonNull(modulePaths, "modulePaths").stream()
            .map(ProjectSourceSet::normalize)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    Objects.requireNonNull(scope, "scope");
    if (rootModulePath.isPresent() != !modulePaths.isEmpty()) {
      throw new IllegalArgumentException("project module identity must match its module graph");
    }
    if (rootModulePath.isPresent() && !modulePaths.contains(rootModulePath.orElseThrow())) {
      throw new IllegalArgumentException("root module configuration must be in the module graph");
    }
    Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(exportedSourcePaths, "exportedSourcePaths");
    Objects.requireNonNull(bindingSourceDocuments, "bindingSourceDocuments");
    jarBindings = List.copyOf(jarBindings);

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
    if (!sourcesByPath.containsKey(primaryPath)) {
      throw new IllegalArgumentException("primary source is not part of the project");
    }
    if (modulePaths.isEmpty() && sourcesByPath.size() != 1) {
      throw new IllegalArgumentException("standalone projects contain exactly one source");
    }
    sources =
        sourcesByPath.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Path::toString)))
            .map(Map.Entry::getValue)
            .toList();
    Set<DocumentId> sourceDocuments =
        sources.stream().map(SourceFile::id).collect(java.util.stream.Collectors.toSet());
    if (!scope.coordinates().keySet().equals(sourceDocuments)) {
      throw new IllegalArgumentException("project scope must describe every source document");
    }
    bindingSourceDocuments = Set.copyOf(bindingSourceDocuments);
    if (!sourceDocuments.containsAll(bindingSourceDocuments)) {
      throw new IllegalArgumentException("binding documents must be part of the project");
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

  public SourceFile primarySource() {
    return source(primaryPath);
  }

  public CompilationRequest compilationRequest() {
    return compilationRequest(primaryPath);
  }

  public CompilationRequest applicationCompilationRequest(Path applicationEntry)
      throws IOException {
    Path entry = normalize(Objects.requireNonNull(applicationEntry, "applicationEntry"));
    if (rootModulePath.isPresent()
        && !entry.getParent().equals(rootModulePath.orElseThrow().getParent())) {
      throw new IOException("application entry and module.norm must be in the same directory");
    }
    return compilationRequest(entry);
  }

  public Set<Path> inputPaths() {
    Set<Path> inputs = new LinkedHashSet<>();
    sources.stream()
        .filter(source -> !bindingSourceDocuments.contains(source.id()))
        .map(SourceFile::path)
        .map(ProjectSourceSet::normalize)
        .forEach(inputs::add);
    inputs.addAll(modulePaths);
    return Set.copyOf(inputs);
  }

  private CompilationRequest compilationRequest(Path selectedPath) {
    SourceFile selected = source(selectedPath);
    Set<DocumentId> exportedDocuments = new LinkedHashSet<>();
    for (SourceFile source : sources) {
      if (exportedSourcePaths.contains(normalize(source.path()))) {
        exportedDocuments.add(source.id());
      }
    }
    return new CompilationRequest(
        new CompilationUnitId(rootModulePath.map(Path::toUri).orElseGet(() -> selected.id().uri())),
        scope,
        selected.id(),
        sources,
        exportedDocuments,
        bindingSourceDocuments);
  }

  private SourceFile source(Path path) {
    Path selected = normalize(path);
    return sources.stream()
        .filter(source -> normalize(source.path()).equals(selected))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("source is not part of the project"));
  }

  private static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }
}
