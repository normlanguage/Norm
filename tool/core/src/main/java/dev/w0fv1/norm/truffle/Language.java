package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.LanguageMetadata;
import dev.w0fv1.norm.value.SourceFile;

@TruffleLanguage.Registration(
    id = LanguageMetadata.ID,
    name = "Norm",
    implementationName = "Norm",
    version = "0.4",
    defaultMimeType = LanguageMetadata.MIME_TYPE,
    characterMimeTypes = LanguageMetadata.MIME_TYPE)
public final class Language extends TruffleLanguage<LanguageContext> {
  private static final ContextReference<LanguageContext> CONTEXT =
      ContextReference.create(Language.class);
  private final TruffleExecutionBackend backend = new TruffleExecutionBackend();

  public Language() {}

  @Override
  protected LanguageContext createContext(Env environment) {
    return new LanguageContext(environment);
  }

  @Override
  protected CallTarget parse(ParsingRequest request) {
    var source = request.getSource();
    var compilation =
        new Compiler()
            .compile(
                SourceFile.of(new DocumentId(source.getURI()), source.getCharacters().toString()));
    if (!compilation.isSuccess()) {
      String message =
          compilation.diagnostics().stream()
              .map(DiagnosticRenderer::render)
              .reduce((left, right) -> left + System.lineSeparator() + right)
              .orElse("Norm compilation failed");
      throw new IllegalArgumentException(message);
    }
    ExecutableProgram executable =
        backend.compile(this, compilation.program().orElseThrow().coreCompilation());
    return new ExecutionRootNode(this, executable.entryPoint()).getCallTarget();
  }

  static LanguageContext context(Node node) {
    return CONTEXT.get(node);
  }
}
