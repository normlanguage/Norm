package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Optional;

public final class DocumentSemanticModel {
  private final SourceFile source;
  private final Syntax.Program syntax;
  private final List<Token> tokens;
  private final SemanticModel projectModel;

  public DocumentSemanticModel(
      SourceFile source, Syntax.Program syntax, List<Token> tokens, SemanticModel projectModel) {
    this.source = java.util.Objects.requireNonNull(source, "source");
    this.syntax = java.util.Objects.requireNonNull(syntax, "syntax");
    this.tokens = List.copyOf(tokens);
    this.projectModel = java.util.Objects.requireNonNull(projectModel, "projectModel");
  }

  public SourceFile source() {
    return source;
  }

  public Syntax.Program syntax() {
    return syntax;
  }

  public List<Token> tokens() {
    return tokens;
  }

  public SemanticModel projectModel() {
    return projectModel;
  }

  public SemanticModel semanticModel() {
    return projectModel.documentView(source, syntax, tokens);
  }

  public Optional<Symbol> symbolAt(int offset) {
    return projectModel.symbolAt(source.id(), offset);
  }

  public Optional<SemanticType> typeAt(int offset) {
    return projectModel.typeAt(source.id(), offset);
  }

  public Optional<SourceSpan> referenceAt(int offset) {
    return projectModel.documentView(source, syntax, tokens).referenceAt(offset);
  }

  public List<Diagnostic> diagnostics() {
    return projectModel.diagnostics().stream()
        .filter(diagnostic -> diagnostic.primarySpan().source().id().equals(source.id()))
        .toList();
  }
}
