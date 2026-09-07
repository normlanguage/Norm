package dev.w0fv1.norm.workspace;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.frontend.CancellationToken;
import dev.w0fv1.norm.frontend.CompilationControl;
import dev.w0fv1.norm.frontend.CompilationLimits;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.project.ProjectLoader;
import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.semantic.AnalysisResult;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.value.CompilationRequest;
import java.io.IOException;
import java.nio.file.Path;
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
  private final Map<DocumentId, DocumentId> aliases;

  private ProjectSession(
      Path root,
      long revision,
      CompilationSnapshot snapshot,
      Set<Path> inputs,
      Optional<String> loadFailure,
      Map<DocumentId, DocumentId> aliases) {
    this.root = root;
    this.revision = revision;
    this.snapshot = snapshot;
    this.inputs = Set.copyOf(inputs);
    this.loadFailure = loadFailure;
    this.aliases = Map.copyOf(aliases);
  }

  static ProjectSession load(
      LanguageService language,
      ProjectLoader projects,
      SourceFile entry,
      Map<Path, SourceFile> openSources,
      long revision,
      CancellationToken cancellation) {
    var control = new CompilationControl(cancellation, CompilationLimits.standard());
    Path root = projects.projectRoot(entry, openSources.values());
    DocumentId standard =
        DocumentId.of(
            "stdlib:/" + root.relativize(normalize(entry.path())).toString().replace('\\', '/'));
    if (language.standardLibrarySource(standard).isPresent()) {
      Map<DocumentId, DocumentId> aliases = new java.util.LinkedHashMap<>();
      Map<DocumentId, SourceFile> overlays = new java.util.LinkedHashMap<>();
      var candidates = new java.util.ArrayList<>(openSources.values());
      candidates.add(entry);
      for (SourceFile source : candidates) {
        Path path = normalize(source.path());
        if (!path.startsWith(root)) continue;
        DocumentId identity =
            DocumentId.of("stdlib:/" + root.relativize(path).toString().replace('\\', '/'));
        if (language.standardLibrarySource(identity).isEmpty()) continue;
        aliases.put(source.id(), identity);
        overlays.put(identity, SourceFile.of(identity, source.text()));
      }
      var snapshot = language.standardLibrarySnapshot(overlays.values(), standard, control);
      Set<Path> inputs =
          aliases.keySet().stream()
              .map(id -> Path.of(id.uri()))
              .collect(java.util.stream.Collectors.toSet());
      return new ProjectSession(root, revision, snapshot, inputs, Optional.empty(), aliases);
    }
    try {
      ProjectSourceSet sourceSet = projects.loadForAnalysis(entry, openSources.values());
      CompilationSnapshot snapshot =
          language.snapshot(sourceSet.analysisCompilationRequest(), control);
      return new ProjectSession(
          sourceSet.root(), revision, snapshot, sourceSet.inputPaths(), Optional.empty(), Map.of());
    } catch (IOException | IllegalArgumentException exception) {
      CompilationSnapshot snapshot = language.snapshot(CompilationRequest.single(entry), control);
      return new ProjectSession(
          root,
          revision,
          snapshot,
          Set.of(normalize(entry.path())),
          Optional.ofNullable(exception.getMessage()).or(() -> Optional.of(exception.toString())),
          Map.of());
    }
  }

  AnalysisResult analysis(SourceFile primary) {
    AnalysisResult selected = snapshot.analysis(aliases.getOrDefault(primary.id(), primary.id()));
    if (loadFailure.isPresent()) {
      List<Diagnostic> diagnostics =
          List.of(
              Diagnostic.error(
                  PROJECT_LOAD,
                  loadFailure.orElseThrow(),
                  new SourceSpan(primary, 0, Math.min(1, primary.length()))));
      return new AnalysisResult(selected.semanticModel(), selected.entryPoint(), diagnostics);
    }
    return selected;
  }

  boolean contains(SourceFile source) {
    return snapshot.document(aliases.getOrDefault(source.id(), source.id())).isPresent();
  }

  SourceFile source(SourceFile source) {
    return snapshot.document(aliases.getOrDefault(source.id(), source.id())).orElseThrow().source();
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

  static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }
}
