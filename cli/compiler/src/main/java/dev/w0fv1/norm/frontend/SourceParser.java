package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.value.SourceFile;
import java.util.List;

final class SourceParser {
  static ParsedDocument parse(SourceFile source) {
    return parse(source, CompilationGuard.unlimited());
  }

  static ParsedDocument parse(SourceFile source, CompilationGuard guard) {
    guard.checkpoint();
    DiagnosticBag diagnostics = new DiagnosticBag();
    List<Token> tokens = new Lexer(source, diagnostics, guard).lex();
    Syntax.Program syntax = new Parser(source, tokens, diagnostics, guard).parse();
    return new ParsedDocument(source, tokens, syntax, diagnostics.snapshot());
  }

  private SourceParser() {}
}
