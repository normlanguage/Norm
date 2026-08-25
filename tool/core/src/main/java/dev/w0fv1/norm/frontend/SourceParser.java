package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;

final class SourceParser {
  private static final DiagnosticCode INVALID_MODULE = new DiagnosticCode("NORM-MODULE-0001");

  static ParsedDocument parse(SourceFile source, boolean manifest) {
    return parse(source, manifest, CompilationGuard.unlimited());
  }

  static ParsedDocument parse(SourceFile source, boolean manifest, CompilationGuard guard) {
    guard.checkpoint();
    DiagnosticBag diagnostics = new DiagnosticBag();
    if (manifest) {
      try {
        new ModuleManifestParser().parse(source);
      } catch (IllegalArgumentException exception) {
        diagnostics.error(
            INVALID_MODULE, exception.getMessage(), new SourceSpan(source, 0, source.length()));
      }
      Syntax.Program syntax =
          new Syntax.Program(
              "",
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              new SourceSpan(source, 0, source.length()));
      return new ParsedDocument(source, List.of(), syntax, diagnostics.snapshot());
    }
    List<Token> tokens = new Lexer(source, diagnostics, guard).lex();
    Syntax.Program syntax = new Parser(source, tokens, diagnostics, guard).parse();
    return new ParsedDocument(source, tokens, syntax, diagnostics.snapshot());
  }

  private SourceParser() {}
}
