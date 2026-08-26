package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.value.BuildMetadata;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.LanguageMetadata;
import dev.w0fv1.norm.value.SourceFile;

@TruffleLanguage.Registration(
    id = LanguageMetadata.ID,
    name = "Norm",
    implementationName = "Norm",
    version = BuildMetadata.VERSION,
    defaultMimeType = LanguageMetadata.MIME_TYPE,
    characterMimeTypes = LanguageMetadata.MIME_TYPE)
public final class Language extends TruffleLanguage<LanguageContext> {
  private static final ContextReference<LanguageContext> CONTEXT =
      ContextReference.create(Language.class);
  private final TruffleExecutionBackend backend = new TruffleExecutionBackend();
  private final ProjectEnvironment projects;

  public Language() {
    try {
      projects = ProjectEnvironment.bootstrap(backend);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("cannot bootstrap Norm language", exception);
    }
  }

  @Override
  protected LanguageContext createContext(Env environment) {
    return new LanguageContext(environment, projects.compilerSession(), projects.projectLoader());
  }

  @Override
  protected CallTarget parse(ParsingRequest request) {
    var source = request.getSource();
    SourceFile sourceFile =
        SourceFile.of(new DocumentId(source.getURI()), source.getCharacters().toString());
    dev.w0fv1.norm.value.CompilationResult compilation;
    try {
      LanguageContext context = CONTEXT.get(null);
      if ("file".equals(source.getURI().getScheme())) {
        ProjectSourceSet sourceSet = context.projects().load(sourceFile, java.util.List.of());
        compilation =
            context.compiler().compile(sourceSet.applicationCompilationRequest(sourceFile.path()));
      } else {
        compilation = context.compiler().compile(sourceFile);
      }
    } catch (java.io.IOException exception) {
      throw new IllegalArgumentException(exception.getMessage(), exception);
    }
    if (!compilation.isSuccess()) {
      String message =
          compilation.diagnostics().stream()
              .map(DiagnosticRenderer::render)
              .reduce((left, right) -> left + System.lineSeparator() + right)
              .orElse("Norm compilation failed");
      throw new IllegalArgumentException(message);
    }
    ExecutableProgram executable =
        backend.compile(this, compilation.program().orElseThrow().compilation().artifact());
    return new ExecutionRootNode(this, executable.entryPoint()).getCallTarget();
  }

  static LanguageContext context(Node node) {
    return CONTEXT.get(node);
  }

  @Override
  protected void disposeContext(LanguageContext context) {
    context.close();
  }
}
