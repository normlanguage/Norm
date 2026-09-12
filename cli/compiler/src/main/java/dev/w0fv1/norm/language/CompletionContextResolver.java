package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.BlockCallChainSyntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import java.util.List;
import java.util.Optional;

public final class CompletionContextResolver {
  public CompletionContextResolver() {}

  public CompletionContext resolve(DocumentSemanticModel document, int offset) {
    String text = document.source().text();
    if (offset < 0 || offset > text.length()) {
      throw new IllegalArgumentException("completion offset is outside the source");
    }
    if (document.tokens().stream()
        .anyMatch(token -> !token.span().source().equals(document.source())))
      return new CompletionContext.None();
    if (insideLiteral(document.tokens(), offset)) return new CompletionContext.None();
    int lineStart = document.source().offsetAt(document.source().positionAt(offset).line() - 1, 0);
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
        && Character.isUnicodeIdentifierPart(text.codePointBefore(identifierStart))) {
      identifierStart -= Character.charCount(text.codePointBefore(identifierStart));
    }
    int previousOffset = previousNonWhitespace(text, identifierStart);
    if (previousOffset >= 0 && text.charAt(previousOffset) == '@') {
      return new CompletionContext.Annotation();
    }
    int identifierEnd = offset;
    while (identifierEnd < text.length()
        && Character.isUnicodeIdentifierPart(text.codePointAt(identifierEnd)))
      identifierEnd += Character.charCount(text.codePointAt(identifierEnd));
    SourceSpan nameSpan = new SourceSpan(document.source(), identifierStart, identifierEnd);
    Optional<CompletionContext.Member> member = memberContext(document, nameSpan);
    if (member.isPresent()) return member.orElseThrow();
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

  private static Optional<CompletionContext.Member> memberContext(
      DocumentSemanticModel document, SourceSpan name) {
    List<Token> tokens = document.tokens();
    int previous = -1;
    for (int index = 0; index < tokens.size(); index++) {
      if (tokens.get(index).span().endOffset() > name.startOffset()) break;
      if (tokens.get(index).kind() != TokenKind.END_OF_FILE) previous = index;
    }
    if (previous < 0) return Optional.empty();
    Token before = tokens.get(previous);
    var model = document.semanticModel();
    if (before.kind() == TokenKind.DOT || before.kind() == TokenKind.QUESTION_DOT) {
      if (previous == 0) return Optional.empty();
      int receiverIndex = previous - 1;
      Optional<SourceSpan> expression =
          model.expressionEndingAt(tokens.get(receiverIndex).span().endOffset());
      while (expression.isEmpty()
          && receiverIndex > 0
          && tokens.get(receiverIndex).kind() == TokenKind.RIGHT_PAREN) {
        receiverIndex--;
        expression = model.expressionEndingAt(tokens.get(receiverIndex).span().endOffset());
      }
      SourceSpan receiver = expression.orElse(tokens.get(previous - 1).span());
      return Optional.of(
          new CompletionContext.Member(
              new MemberAccessSite(
                  receiver, name, MemberAccessSite.Form.EXPLICIT, Optional.empty())));
    }
    if (before.kind() != TokenKind.RIGHT_BRACE || previous + 1 >= tokens.size())
      return Optional.empty();
    Token prefix = tokens.get(previous + 1);
    if (prefix.span().startOffset() != name.startOffset()
        || !BlockCallChainSyntax.isPrefix(before, prefix)) return Optional.empty();
    if (previous + 2 < tokens.size()) {
      TokenKind following = tokens.get(previous + 2).kind();
      if (following == TokenKind.LESS
          || following == TokenKind.LEFT_PAREN
          || following == TokenKind.COLON) return Optional.empty();
    }
    Optional<SourceSpan> opening =
        previous + 2 < tokens.size() && tokens.get(previous + 2).kind() == TokenKind.LEFT_BRACE
            ? Optional.of(tokens.get(previous + 2).span())
            : Optional.empty();
    if (opening.isPresent() && !BlockCallChainSyntax.isHead(tokens, previous + 1))
      return Optional.empty();
    return model
        .callEndingAt(before.span().endOffset())
        .map(
            receiver ->
                new CompletionContext.Member(
                    new MemberAccessSite(
                        receiver, name, MemberAccessSite.Form.BLOCK_CONTINUATION, opening)));
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

  private static boolean insideLiteral(List<Token> tokens, int offset) {
    var strings = new java.util.ArrayDeque<Boolean>();
    for (Token token : tokens) {
      if (token.span().startOffset() >= offset) break;
      if (token.kind() == TokenKind.STRING || token.kind() == TokenKind.CODE_POINT) {
        if (offset < token.span().endOffset()) return true;
      }
      if (token.kind() == TokenKind.UNTERMINATED_LITERAL && offset <= token.span().endOffset())
        return true;
      if (token.span().endOffset() > offset) break;
      switch (token.kind()) {
        case INTERPOLATED_STRING_START -> strings.addFirst(true);
        case INTERPOLATION_START -> {
          strings.removeFirst();
          strings.addFirst(false);
        }
        case INTERPOLATION_END -> {
          strings.removeFirst();
          strings.addFirst(true);
        }
        case INTERPOLATED_STRING_END -> strings.removeFirst();
        default -> {}
      }
    }
    return !strings.isEmpty() && strings.getFirst();
  }
}
