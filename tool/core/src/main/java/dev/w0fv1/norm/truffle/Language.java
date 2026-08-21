package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.LanguageMetadata;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.TypedProgram;
import java.io.PrintWriter;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;

@TruffleLanguage.Registration(
    id = LanguageMetadata.ID,
    name = "Norm",
    implementationName = "Norm",
    version = "0.1",
    defaultMimeType = LanguageMetadata.MIME_TYPE,
    characterMimeTypes = LanguageMetadata.MIME_TYPE)
public final class Language extends TruffleLanguage<LanguageContext> {
  private static final ThreadLocal<PreparedExecution> PREPARED = new ThreadLocal<>();
  private static final ContextReference<LanguageContext> CONTEXT =
      ContextReference.create(Language.class);

  public Language() {}

  public static void execute(TypedProgram program, PrintWriter output) {
    PREPARED.set(new PreparedExecution(program, output));
    try (Context context = Context.newBuilder(LanguageMetadata.ID).build()) {
      context.eval(
          org.graalvm.polyglot.Source.newBuilder(
                  LanguageMetadata.ID, "", program.syntax().span().source().path().toString())
              .buildLiteral());
    } finally {
      PREPARED.remove();
    }
  }

  @Override
  protected LanguageContext createContext(Env environment) {
    return new LanguageContext(environment);
  }

  @Override
  protected CallTarget parse(ParsingRequest request) {
    PreparedExecution prepared = PREPARED.get();
    if (prepared != null) {
      return new Lowerer(this, prepared.output()).lower(prepared.program()).entryPoint();
    }
    var source = request.getSource();
    Path path = source.getPath() == null ? Path.of(source.getName()) : Path.of(source.getPath());
    var compilation =
        new Compiler().compile(SourceFile.of(path, source.getCharacters().toString()));
    if (!compilation.isSuccess()) {
      String message =
          compilation.diagnostics().stream()
              .map(DiagnosticRenderer::render)
              .reduce((left, right) -> left + System.lineSeparator() + right)
              .orElse("Norm compilation failed");
      throw new IllegalArgumentException(message);
    }
    return new Lowerer(this, CONTEXT.get(null).output())
        .lower(compilation.program().orElseThrow())
        .entryPoint();
  }

  private record PreparedExecution(TypedProgram program, PrintWriter output) {}
}
