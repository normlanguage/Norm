package dev.w0fv1.norm.polyglot;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.application.ApplicationInput;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import dev.w0fv1.norm.value.BuildMetadata;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.LanguageMetadata;

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
    try {
      LanguageContext context = CONTEXT.get(null);
      ApplicationInput input;
      if ("file".equals(source.getURI().getScheme())) {
        ProjectSourceSet sourceSet = context.projects().load(sourceFile, java.util.List.of());
        input =
            new ApplicationInput(
                sourceSet.applicationCompilationRequest(sourceFile.path()),
                java.util.Optional.of(sourceSet));
      } else {
        input =
            new ApplicationInput(CompilationRequest.single(sourceFile), java.util.Optional.empty());
      }
      var application = context.application(input);
      var target =
          backend.instrumentedEntryPoint(
              this,
              application.result().output().orElseThrow().artifact(),
              application.executionPlan());
      return new ExecutionRootNode(this, input, target).getCallTarget();
    } catch (java.io.IOException exception) {
      throw new IllegalArgumentException(exception.getMessage(), exception);
    }
  }

  static LanguageContext context(Node node) {
    return CONTEXT.get(node);
  }

  @Override
  protected void disposeContext(LanguageContext context) {
    context.close();
  }

  @Override
  protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
    return true;
  }
}
