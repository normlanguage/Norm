package dev.w0fv1.norm.project;

import dev.w0fv1.norm.frontend.CompilationCancelledException;
import dev.w0fv1.norm.frontend.CompilationControl;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleDeclaration;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProjectLoadContext {
  private final ModuleEvaluator evaluator;
  private final CompilationControl control;
  private final Map<DocumentId, ModuleEvaluation> replayed;
  private final Map<DocumentId, ModuleEvaluation> evaluated = new LinkedHashMap<>();
  private final ProjectInputTracker inputs = new ProjectInputTracker();
  private final Map<LatestModule, Integer> latestVersions = new LinkedHashMap<>();

  ProjectLoadContext(
      ModuleEvaluator evaluator, CompilationControl control, List<ModuleEvaluation> replayed) {
    this.evaluator = evaluator;
    this.control = control;
    this.replayed = new LinkedHashMap<>();
    for (var evaluation : replayed) {
      if (this.replayed.putIfAbsent(evaluation.source().id(), evaluation) != null)
        throw new IllegalArgumentException("duplicate replayed module evaluation");
    }
  }

  ProjectInputTracker inputs() {
    return inputs;
  }

  dev.w0fv1.norm.value.ModuleRequirement resolve(
      dev.w0fv1.norm.value.ModuleDependency dependency,
      dev.w0fv1.norm.packages.NormPackageResolver packages)
      throws IOException {
    checkpoint();
    if (dependency.version().isPresent()) return packages.resolve(dependency);
    var key = new LatestModule(dependency.repository(), dependency.name());
    var version = latestVersions.get(key);
    if (version == null) {
      version = packages.resolve(dependency).version();
      checkpoint();
      latestVersions.put(key, version);
    }
    return dependency.resolved(version);
  }

  void checkpoint() {
    if (control.cancellation().isCancellationRequested()) throw new CompilationCancelledException();
  }

  ModuleDeclaration evaluate(SourceFile source) throws IOException {
    checkpoint();
    var current = evaluated.get(source.id());
    if (current != null && current.source().text().equals(source.text()))
      return current.declaration();
    var prior = replayed.remove(source.id());
    var result =
        prior != null && prior.source().text().equals(source.text())
            ? prior
            : evaluator.evaluate(source, control);
    checkpoint();
    evaluated.put(source.id(), result);
    return result.declaration();
  }

  ProjectLoadResult finish(ProjectSourceSet sources) {
    checkpoint();
    return new ProjectLoadResult(sources, inputs.snapshot(), List.copyOf(evaluated.values()));
  }

  private record LatestModule(dev.w0fv1.norm.value.ModuleRepositoryId repository, String name) {}
}
