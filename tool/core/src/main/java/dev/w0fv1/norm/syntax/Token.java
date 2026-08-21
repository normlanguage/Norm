package dev.w0fv1.norm.syntax;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.Objects;

public record Token(TokenKind kind, String lexeme, String value, SourceSpan span) {
  public Token {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(lexeme, "lexeme");
    Objects.requireNonNull(span, "span");
  }

  public static Token simple(TokenKind kind, String lexeme, SourceSpan span) {
    return new Token(kind, lexeme, null, span);
  }
}
