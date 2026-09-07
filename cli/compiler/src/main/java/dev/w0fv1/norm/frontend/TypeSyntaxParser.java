package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TypeSyntaxParser {
  private TypeSyntaxParser() {}

  public static Optional<Syntax.TypeRef> type(List<Token> tokens) {
    return tokens.isEmpty() ? Optional.empty() : parser(tokens).typeFragment();
  }

  public static Optional<List<Syntax.TypeRef>> arguments(List<Token> tokens) {
    return tokens.isEmpty() ? Optional.of(List.of()) : parser(tokens).typeArgumentsFragment();
  }

  private static Parser parser(List<Token> tokens) {
    var input = new ArrayList<>(tokens);
    SourceSpan last = tokens.getLast().span();
    input.add(
        Token.simple(TokenKind.END_OF_FILE, "", SourceSpan.at(last.source(), last.endOffset())));
    return new Parser(last.source(), input, new DiagnosticBag());
  }
}
