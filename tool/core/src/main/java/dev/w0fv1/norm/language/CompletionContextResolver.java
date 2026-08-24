package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import java.util.List;

public final class CompletionContextResolver {
  public CompletionContextResolver() {}

  public CompletionContext resolve(DocumentSemanticModel document, int offset) {
    String text = document.source().text();
    if (offset < 0 || offset > text.length()) {
      throw new IllegalArgumentException("completion offset is outside the source");
    }
    if (insideExcludedText(text, offset)) return new CompletionContext.None();
    int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
    String line = text.substring(lineStart, offset);
    int firstContent = 0;
    while (firstContent < line.length() && Character.isWhitespace(line.charAt(firstContent))) {
      firstContent++;
    }
    if (line.startsWith("import ", firstContent)) {
      return new CompletionContext.Import(lineStart + firstContent + "import ".length());
    }
    int identifierStart = offset;
    while (identifierStart > 0
        && Character.isUnicodeIdentifierPart(text.charAt(identifierStart - 1))) {
      identifierStart--;
    }
    int previousOffset = previousNonWhitespace(text, identifierStart);
    if (previousOffset >= 0 && text.charAt(previousOffset) == '.') {
      return new CompletionContext.Member(previousOffset);
    }
    if (previousOffset > 0
        && text.charAt(previousOffset) == ':'
        && text.charAt(previousOffset - 1) == ':') {
      return new CompletionContext.Member(previousOffset - 1);
    }
    List<Token> tokens =
        document.tokens().stream().filter(token -> token.span().startOffset() < offset).toList();
    TokenKind previous = tokens.isEmpty() ? null : tokens.getLast().kind();
    if (expectsInterfaceType(tokens)) {
      return new CompletionContext.InterfaceType();
    }
    if (insideTypeArguments(tokens)) return new CompletionContext.TypeArgument();
    if (previous == TokenKind.CLASS || previous == TokenKind.COLON) {
      return new CompletionContext.Type();
    }
    if (insideArguments(tokens)
        && (previous == TokenKind.LEFT_PAREN || previous == TokenKind.COMMA)) {
      return new CompletionContext.ArgumentLabel();
    }
    int braces = balance(tokens, TokenKind.LEFT_BRACE, TokenKind.RIGHT_BRACE);
    if (braces == 0) return new CompletionContext.TopLevel();
    if (previous == TokenKind.LEFT_BRACE || previous == TokenKind.SEMICOLON) {
      return new CompletionContext.Statement();
    }
    return new CompletionContext.Expression();
  }

  private static boolean insideTypeArguments(List<Token> tokens) {
    return balance(tokens, TokenKind.LESS, TokenKind.GREATER) > 0;
  }

  private static boolean insideArguments(List<Token> tokens) {
    return balance(tokens, TokenKind.LEFT_PAREN, TokenKind.RIGHT_PAREN) > 0;
  }

  private static boolean expectsInterfaceType(List<Token> tokens) {
    int closedTypeArguments = 0;
    boolean comma = false;
    boolean insideTypeParameters = insideTypeArguments(tokens);
    for (int index = tokens.size() - 1; index >= 0; index--) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.GREATER) {
        closedTypeArguments++;
        continue;
      }
      if (kind == TokenKind.LESS) {
        if (closedTypeArguments == 0) return false;
        closedTypeArguments--;
        continue;
      }
      if (closedTypeArguments > 0) continue;
      if (kind == TokenKind.COMMA) {
        comma = true;
        continue;
      }
      if (kind == TokenKind.IMPLEMENTS) return true;
      if (kind == TokenKind.EXTENDS) return !insideTypeParameters || !comma;
      if (kind == TokenKind.LEFT_BRACE
          || kind == TokenKind.RIGHT_BRACE
          || kind == TokenKind.SEMICOLON
          || kind == TokenKind.EQUAL
          || kind == TokenKind.LEFT_PAREN) {
        return false;
      }
    }
    return false;
  }

  private static int balance(List<Token> tokens, TokenKind open, TokenKind close) {
    int balance = 0;
    for (Token token : tokens) {
      if (token.kind() == open) balance++;
      if (token.kind() == close && balance > 0) balance--;
    }
    return balance;
  }

  private static int previousNonWhitespace(String text, int offset) {
    int current = offset - 1;
    while (current >= 0 && Character.isWhitespace(text.charAt(current))) current--;
    return current;
  }

  private static boolean insideExcludedText(String text, int offset) {
    boolean string = false;
    boolean lineComment = false;
    boolean blockComment = false;
    boolean escaped = false;
    for (int index = 0; index < offset; index++) {
      char current = text.charAt(index);
      char next = index + 1 < offset ? text.charAt(index + 1) : 0;
      if (lineComment) {
        if (current == '\n') lineComment = false;
        continue;
      }
      if (blockComment) {
        if (current == '*' && next == '/') {
          blockComment = false;
          index++;
        }
        continue;
      }
      if (string) {
        if (escaped) {
          escaped = false;
        } else if (current == '\\') {
          escaped = true;
        } else if (current == '"') {
          string = false;
        }
        continue;
      }
      if (current == '"') string = true;
      if (current == '/' && next == '/') {
        lineComment = true;
        index++;
      } else if (current == '/' && next == '*') {
        blockComment = true;
        index++;
      }
    }
    return string || lineComment || blockComment;
  }
}
