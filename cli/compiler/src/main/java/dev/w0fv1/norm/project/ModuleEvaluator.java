package dev.w0fv1.norm.project;

import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.LanguageProfile;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDeclaration;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
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
            List<String> dependencyRepositories = []
            List<String> dependencyNames = []
            List<Integer?> dependencyVersions = []
            List<Boolean> dependencyExports = []
            for ModuleRequirement requirement : definition.dependencies() {
              dependencyRepositories.add(requirement.repository())
              dependencyNames.add(requirement.name())
              dependencyVersions.add(requirement.version())
              dependencyExports.add(requirement.exported())
            }
            String bindingSource = ""
            String bindingPath = ""
            String bindingGroup = ""
            String bindingArtifact = ""
            String bindingVersion = ""
            String bindingDigest = ""
            List<String> bindingApiTypes = []
            List<List<String>> bindingApiMembers = []
            List<List<String>> bindingApiOverloadNames = []
            List<List<List<String>>> bindingApiOverloadParameterTypes = []
            JarBinding? binding = definition.binding()
            if binding != null {
              JarTarget target = binding.target()
              bindingSource = target.source()
              bindingPath = target.path()
              bindingGroup = target.group()
              bindingArtifact = target.artifact()
              bindingVersion = target.version()
              ContentDigest? digest = target.digest()
              if digest != null {
                bindingDigest = digest.value()
              }
              for JarType type : binding.api() {
                bindingApiTypes.add(type.name())
                bindingApiMembers.add(type.members())
                List<String> overloadNames = []
                List<List<String>> overloadParameterTypes = []
                for JarOverload overload : type.overloads() {
                  overloadNames.add(overload.name())
                  overloadParameterTypes.add(overload.parameterTypes())
                }
                bindingApiOverloadNames.add(overloadNames)
                bindingApiOverloadParameterTypes.add(overloadParameterTypes)
              }
            }
            __publishModule(
              name: definition.name(),
              version: definition.version(),
              exports: definition.exports(),
              dependencyRepositories: dependencyRepositories,
              dependencyNames: dependencyNames,
              dependencyVersions: dependencyVersions,
              dependencyExports: dependencyExports,
              bindingSource: bindingSource,
              bindingPath: bindingPath,
              bindingGroup: bindingGroup,
              bindingArtifact: bindingArtifact,
              bindingVersion: bindingVersion,
              bindingDigest: bindingDigest,
              bindingApiTypes: bindingApiTypes,
              bindingApiMembers: bindingApiMembers,
              bindingApiOverloadNames: bindingApiOverloadNames,
              bindingApiOverloadParameterTypes: bindingApiOverloadParameterTypes,
              sourceRoots: definition.sources(),
              testRoots: definition.tests()
            )
          }
          """);
  private final CompilerSession compiler;
  private final ExecutionBackend backend;
  private final java.util.ArrayList<ModuleEvaluation> evaluations = new java.util.ArrayList<>();
  private final java.util.Map<DocumentId, ModuleEvaluation> replayed =
      new java.util.LinkedHashMap<>();

  void replay(List<ModuleEvaluation> values) {
    for (var value : values) replayed.put(value.source().id(), value);
  }

  List<ModuleEvaluation> evaluations() {
    return List.copyOf(evaluations);
  }

  void clearEvaluations() {
    evaluations.clear();
  }

  ModuleEvaluator(LanguageProfile profile, ExecutionBackend backend) {
    this(new CompilerSession(profile.moduleEvaluation(ENTRY.id())), backend);
  }

  private ModuleEvaluator(CompilerSession compiler, ExecutionBackend backend) {
    this.compiler = compiler;
    this.backend = Objects.requireNonNull(backend, "backend");
  }

  static ModuleEvaluator persistent(LanguageProfile profile, ExecutionBackend backend)
      throws IOException {
    return new ModuleEvaluator(
        CompilerSession.persistent(profile.moduleEvaluation(ENTRY.id())), backend);
  }

  ModuleDeclaration evaluate(SourceFile source) throws IOException {
    var previous = replayed.remove(source.id());
    if (previous != null && previous.source().text().equals(source.text())) {
      evaluations.add(previous);
      return previous.declaration();
    }
    var result = compiler.compile(request(source));
    if (!result.isSuccess()) {
      throw new ModuleCompilationException(result.diagnostics());
    }
    var artifact = result.output().orElseThrow().artifact();
    var evaluated =
        ModuleEvaluation.evaluate(
            source, artifact, dev.w0fv1.norm.core.CoreExecutionPlan.forArtifact(artifact), backend);
    evaluations.add(evaluated);
    return evaluated.declaration();
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
}
