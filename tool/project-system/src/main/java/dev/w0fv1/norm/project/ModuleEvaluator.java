package dev.w0fv1.norm.project;

import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.ModulePublisher;
import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.LanguageProfile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ModuleEvaluator implements AutoCloseable {
  private static final ModuleCoordinate EVALUATION_MODULE =
      new ModuleCoordinate("norm.bootstrap", 1);
  private static final SourceFile ENTRY =
      SourceFile.of(
          DocumentId.of("bootstrap:/evaluate-module.norm"),
          """
          Void main() {
            Module definition = module()
            List<String> dependencyNames = []
            List<Integer> dependencyVersions = []
            for ModuleRequirement requirement : definition.dependencies() {
              dependencyNames.add(requirement.name())
              dependencyVersions.add(requirement.version())
            }
            __publishModule(
              name: definition.name(),
              version: definition.version(),
              exports: definition.exports(),
              dependencyNames: dependencyNames,
              dependencyVersions: dependencyVersions
            )
          }
          """);
  private final CompilerSession compiler;
  private final ExecutionBackend backend;

  ModuleEvaluator(LanguageProfile profile, ExecutionBackend backend) {
    compiler = new CompilerSession(profile.moduleEvaluation(ENTRY.id()));
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  ModuleDescriptor evaluate(SourceFile source) throws IOException {
    var result = compiler.compile(request(source));
    if (!result.isSuccess()) {
      String diagnostics =
          result.diagnostics().stream()
              .map(DiagnosticRenderer::render)
              .reduce((left, right) -> left + System.lineSeparator() + right)
              .orElse("module compilation failed");
      throw new IOException(diagnostics);
    }
    Publication publication = new Publication();
    try {
      backend.execute(
          result.program().orElseThrow().compilation().artifact(),
          ExecutionContext.module(publication));
      return publication.descriptor();
    } catch (IllegalArgumentException | IllegalStateException | NormExecutionException exception) {
      throw new IOException(
          "invalid " + source.displayName() + ": " + exception.getMessage(), exception);
    }
  }

  CompilationSnapshot snapshot(SourceFile source) {
    return compiler.snapshot(request(source));
  }

  private static CompilationRequest request(SourceFile source) {
    var coordinates = new LinkedHashMap<DocumentId, ModuleSourceCoordinate>();
    coordinates.put(source.id(), new ModuleSourceCoordinate(EVALUATION_MODULE, "module.norm"));
    coordinates.put(
        ENTRY.id(),
        new ModuleSourceCoordinate(EVALUATION_MODULE, "bootstrap/evaluate-module.norm"));
    var request =
        new CompilationRequest(
            new CompilationUnitId(source.id().uri()),
            new CompilationScope(coordinates),
            ENTRY.id(),
            List.of(source, ENTRY),
            Set.of());
    return request;
  }

  @Override
  public void close() {
    compiler.close();
  }

  private static final class Publication implements ModulePublisher {
    private ModuleDescriptor descriptor;

    @Override
    public void publish(ModuleDescriptor value) {
      if (descriptor != null) {
        throw new IllegalStateException("module configuration produced more than one definition");
      }
      descriptor = Objects.requireNonNull(value, "value");
    }

    ModuleDescriptor descriptor() {
      if (descriptor == null) {
        throw new IllegalStateException("module configuration did not produce a definition");
      }
      return descriptor;
    }
  }
}
