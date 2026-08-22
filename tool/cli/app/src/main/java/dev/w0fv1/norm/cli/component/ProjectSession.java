package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.ProjectLoader;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ProjectSession {
  private static final DiagnosticCode PROJECT_LOAD = new DiagnosticCode("NORM-PROJECT-0001");
  private final Path root;
  private final long revision;
  private final CompilationSnapshot snapshot;
  private final Set<Path> inputs;
  private final Optional<String> loadFailure;

  private ProjectSession(
      Path root,
      long revision,
      CompilationSnapshot snapshot,
      Set<Path> inputs,
      Optional<String> loadFailure) {
    this.root = root;
    this.revision = revision;
    this.snapshot = snapshot;
    this.inputs = Set.copyOf(inputs);
    this.loadFailure = loadFailure;
  }

  static ProjectSession load(
      LanguageService language,
      SourceFile entry,
      Map<Path, SourceFile> openSources,
      long revision) {
    Path root = rootOf(entry.path());
    try {
      List<Path> entries;
      Path manifest = root.resolve("module.norm");
      if (Files.isRegularFile(manifest)) {
        try (var paths = Files.walk(root)) {
          entries =
              paths
                  .filter(Files::isRegularFile)
                  .filter(path -> path.getFileName().toString().endsWith(".norm"))
                  .filter(path -> !path.getFileName().toString().equals("module.norm"))
                  .map(ProjectSession::normalize)
                  .sorted(Comparator.comparing(Path::toString))
                  .toList();
        }
      } else {
        entries = List.of(normalize(entry.path()));
      }
      Map<Path, SourceFile> sources = new LinkedHashMap<>();
      Set<Path> exported = new LinkedHashSet<>();
      ProjectLoader loader = new ProjectLoader();
      for (Path candidate : entries) {
        CompilationRequest request = loader.load(candidate);
        Map<DocumentId, Path> pathsById = new LinkedHashMap<>();
        for (SourceFile source : request.sources()) {
          Path path = normalize(source.path());
          pathsById.put(source.id(), path);
          sources.putIfAbsent(path, source);
        }
        for (DocumentId exportedSource : request.exportedSources()) {
          Path path = pathsById.get(exportedSource);
          if (path != null) exported.add(path);
        }
      }
      openSources.forEach((path, source) -> sources.put(normalize(path), source));
      Set<Path> inputs = new LinkedHashSet<>(sources.keySet());
      if (Files.isRegularFile(manifest)) inputs.add(normalize(manifest));
      List<SourceFile> projectSources =
          sources.entrySet().stream()
              .sorted(Map.Entry.comparingByKey(Comparator.comparing(Path::toString)))
              .map(Map.Entry::getValue)
              .toList();
      Set<DocumentId> exportedDocuments =
          projectSources.stream()
              .filter(source -> exported.contains(normalize(source.path())))
              .map(SourceFile::id)
              .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
      CompilationSnapshot snapshot =
          language.snapshot(new CompilationRequest(entry.id(), projectSources, exportedDocuments));
      return new ProjectSession(root, revision, snapshot, inputs, Optional.empty());
    } catch (IOException | IllegalArgumentException exception) {
      CompilationSnapshot snapshot = language.snapshot(CompilationRequest.single(entry));
      return new ProjectSession(
          root,
          revision,
          snapshot,
          Set.of(normalize(entry.path())),
          Optional.ofNullable(exception.getMessage()).or(() -> Optional.of(exception.toString())));
    }
  }

  AnalysisResult analysis(SourceFile primary) {
    AnalysisResult selected = snapshot.analysis(primary.id());
    if (loadFailure.isPresent()) {
      List<Diagnostic> diagnostics = new ArrayList<>(selected.diagnostics());
      diagnostics.add(
          0,
          Diagnostic.error(
              PROJECT_LOAD,
              loadFailure.orElseThrow(),
              new SourceSpan(primary, 0, Math.min(1, primary.length()))));
      return new AnalysisResult(
          selected.semanticModel(), selected.entryPoint(), selected.boundProgram(), diagnostics);
    }
    return selected;
  }

  long revision() {
    return revision;
  }

  CompilationSnapshot snapshot() {
    return snapshot;
  }

  Path root() {
    return root;
  }

  Set<Path> inputs() {
    return inputs;
  }

  static Path rootOf(Path source) {
    Path current = normalize(source).getParent();
    Path fallback = current;
    while (current != null) {
      if (Files.isRegularFile(current.resolve("module.norm"))) return current;
      current = current.getParent();
    }
    return fallback;
  }

  static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }
}
