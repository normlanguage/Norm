package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.SourceLocation;

final class ImportEditBuilder {
  CompletionTextEdit create(DocumentSemanticModel document, String qualifiedName) {
    if (!document.syntax().imports().isEmpty()) {
      int offset = document.syntax().imports().getLast().span().endOffset();
      return edit(document, offset, "\nimport " + qualifiedName);
    }
    if (!document.syntax().packageName().isEmpty()) {
      var tokens = document.tokens();
      int offset =
          java.util.stream.IntStream.range(1, tokens.size())
              .takeWhile(
                  index ->
                      tokens.get(index).kind() == TokenKind.IDENTIFIER
                          || tokens.get(index).kind() == TokenKind.DOT)
              .map(index -> tokens.get(index).span().endOffset())
              .reduce((first, second) -> second)
              .orElse(tokens.getFirst().span().endOffset());
      return edit(document, offset, "\n\nimport " + qualifiedName);
    }
    return edit(document, 0, "import " + qualifiedName + "\n\n");
  }

  private static CompletionTextEdit edit(DocumentSemanticModel document, int offset, String text) {
    return new CompletionTextEdit(new SourceLocation(document.source().id(), offset, offset), text);
  }
}
