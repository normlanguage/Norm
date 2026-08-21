package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnalysisResult;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.TypedProgram;
import java.util.Optional;

public final class Compiler {
  public Compiler() {}

  public CompilationResult compile(SourceFile source) {
    AnalysisResult analysis = analyze(source);
    if (analysis.hasErrors() || analysis.entryPoint().isEmpty()) {
      return new CompilationResult(Optional.empty(), analysis.diagnostics());
    }
    return new CompilationResult(
        Optional.of(
            new TypedProgram(analysis.semanticModel(), analysis.entryPoint().orElseThrow())),
        analysis.diagnostics());
  }

  public AnalysisResult analyze(SourceFile source) {
    java.util.Objects.requireNonNull(source, "source");
    DiagnosticBag diagnostics = new DiagnosticBag();
    var tokens = new Lexer(source, diagnostics).lex();
    Syntax.Program syntax = new Parser(source, tokens, diagnostics).parse();
    return new Analyzer(syntax, diagnostics).analyze();
  }
}
