package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.syntax.TokenKind;
import dev.w0fv1.norm.value.SourceLocation;

final class ImportEditBuilder {
  CompletionTextEdit create(DocumentSemanticModel document, String qualifiedName) {
    String newline = document.source().lineSeparator();
    if (!document.syntax().imports().isEmpty()) {
      int offset = document.syntax().imports().getLast().span().endOffset();
      return edit(document, offset, newline + "import " + qualifiedName);
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
      return edit(document, offset, newline + newline + "import " + qualifiedName);
    }
    return edit(document, 0, "import " + qualifiedName + newline + newline);
  }

  private static CompletionTextEdit edit(DocumentSemanticModel document, int offset, String text) {
    return new CompletionTextEdit(new SourceLocation(document.source().id(), offset, offset), text);
  }
}
