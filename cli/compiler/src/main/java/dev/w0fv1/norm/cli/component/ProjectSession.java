package dev.w0fv1.norm.cli.component;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.project.ProjectLoader;
import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
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
      ProjectLoader projects,
      SourceFile entry,
      Map<Path, SourceFile> openSources,
      long revision) {
    try {
      ProjectSourceSet sourceSet = projects.loadForAnalysis(entry, openSources.values());
      CompilationSnapshot snapshot = language.snapshot(sourceSet.analysisCompilationRequest());
      return new ProjectSession(
          sourceSet.root(), revision, snapshot, sourceSet.inputPaths(), Optional.empty());
    } catch (IOException | IllegalArgumentException exception) {
      CompilationSnapshot snapshot = language.snapshot(CompilationRequest.single(entry));
      return new ProjectSession(
          projects.projectRoot(entry, openSources.values()),
          revision,
          snapshot,
          Set.of(normalize(entry.path())),
          Optional.ofNullable(exception.getMessage()).or(() -> Optional.of(exception.toString())));
    }
  }

  AnalysisResult analysis(SourceFile primary) {
    AnalysisResult selected = snapshot.analysis(primary.id());
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
