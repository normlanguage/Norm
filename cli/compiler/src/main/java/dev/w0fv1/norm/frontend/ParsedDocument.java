package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.value.SourceFile;
import java.util.List;

record ParsedDocument(
    SourceFile source, List<Token> tokens, Syntax.Program syntax, List<Diagnostic> diagnostics) {
  ParsedDocument {
    tokens = List.copyOf(tokens);
    diagnostics = List.copyOf(diagnostics);
  }
}
