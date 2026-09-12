package dev.w0fv1.norm.syntax;

import dev.w0fv1.norm.source.SourceSpan;
import java.util.List;

public final class BlockCallChainSyntax {
  private BlockCallChainSyntax() {}

  public static boolean isHead(List<Token> tokens, int nameIndex) {
    if (nameIndex < 1 || nameIndex + 1 >= tokens.size()) return false;
    Token name = tokens.get(nameIndex);
    Token opening = tokens.get(nameIndex + 1);
    return isPrefix(tokens.get(nameIndex - 1), name)
        && opening.kind() == TokenKind.LEFT_BRACE
        && sameLine(name.span(), opening.span());
  }

  public static boolean isPrefix(Token closing, Token name) {
    return closing.kind() == TokenKind.RIGHT_BRACE
        && name.kind() == TokenKind.IDENTIFIER
        && closing.span().endOffset() <= name.span().startOffset()
        && sameLine(closing.span(), name.span());
  }

  private static boolean sameLine(SourceSpan left, SourceSpan right) {
    return left.source().equals(right.source())
        && left.source().positionAt(left.startOffset()).line()
            == right.source().positionAt(right.startOffset()).line();
  }
}
