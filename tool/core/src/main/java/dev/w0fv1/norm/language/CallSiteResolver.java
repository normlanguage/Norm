package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

final class CallSiteResolver {
  private final TypeReferenceResolver typeReferences = new TypeReferenceResolver();
  private final SymbolSpecializer symbols = new SymbolSpecializer();

  Optional<CallSite> resolve(DocumentSemanticModel document, int offset) {
    List<Token> tokens =
        document.tokens().stream()
            .filter(token -> token.kind() != TokenKind.END_OF_FILE)
            .filter(token -> token.span().startOffset() < offset)
            .toList();
    int opening = activeOpeningParenthesis(tokens);
    if (opening < 0) return Optional.empty();
    int nameIndex = callableName(tokens, opening);
    if (nameIndex < 0) return Optional.empty();
    Optional<Symbol> callable = callable(document.semanticModel(), tokens, nameIndex, offset);
    if (callable.isEmpty()) return Optional.empty();
    Symbol symbol =
        symbols.specialize(
            callable.orElseThrow(),
            typeReferences
                .arguments(document, tokens, nameIndex + 1, opening, offset)
                .orElse(List.of()));
    return Optional.of(new CallSite(symbol, activeParameter(tokens, opening, symbol)));
  }

  private static int activeOpeningParenthesis(List<Token> tokens) {
    ArrayDeque<Integer> openings = new ArrayDeque<>();
    for (int index = 0; index < tokens.size(); index++) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.LEFT_PAREN) openings.addLast(index);
      if (kind == TokenKind.RIGHT_PAREN && !openings.isEmpty()) openings.removeLast();
    }
    return openings.isEmpty() ? -1 : openings.getLast();
  }

  private static int callableName(List<Token> tokens, int opening) {
    int index = opening - 1;
    if (index >= 0 && tokens.get(index).kind() == TokenKind.GREATER) {
      int depth = 0;
      while (index >= 0) {
        TokenKind kind = tokens.get(index).kind();
        if (kind == TokenKind.GREATER) depth++;
        if (kind == TokenKind.LESS && --depth == 0) {
          index--;
          break;
        }
        index--;
      }
    }
    return index >= 0 && tokens.get(index).kind() == TokenKind.IDENTIFIER ? index : -1;
  }

  private static Optional<Symbol> callable(
      SemanticModel model, List<Token> tokens, int nameIndex, int offset) {
    Token name = tokens.get(nameIndex);
    Optional<Symbol> bound = model.resolvedSymbolOf(name.span()).filter(CallSiteResolver::callable);
    if (bound.isPresent()) return bound;
    if (nameIndex >= 2
        && (tokens.get(nameIndex - 1).kind() == TokenKind.DOT
            || tokens.get(nameIndex - 1).kind() == TokenKind.QUESTION_DOT)) {
      int receiverOffset = Math.max(0, tokens.get(nameIndex - 1).span().startOffset() - 1);
      Optional<Symbol> receiver = model.symbolAt(receiverOffset);
      if (receiver.isPresent() && receiver.orElseThrow().kind() == SymbolKind.TYPE) {
        return model.typeMembers(receiver.orElseThrow().name()).stream()
            .filter(symbol -> symbol.name().equals(name.lexeme()))
            .findFirst();
      }
      return model.typeAt(receiverOffset).stream()
          .flatMap(type -> model.members(type).stream())
          .filter(symbol -> symbol.name().equals(name.lexeme()))
          .findFirst();
    }
    Optional<Symbol> visible =
        model.visibleSymbols(offset).stream()
            .filter(symbol -> symbol.name().equals(name.lexeme()))
            .map(model::resolveAlias)
            .filter(CallSiteResolver::callable)
            .findFirst();
    if (visible.isPresent()) return visible;
    return model.symbols().stream()
        .filter(symbol -> symbol.name().equals(name.lexeme()))
        .filter(symbol -> symbol.owner().isEmpty())
        .filter(
            symbol ->
                symbol.declaration().isEmpty()
                    || symbol.declaration().orElseThrow().document().equals(model.source().id()))
        .filter(CallSiteResolver::callable)
        .findFirst();
  }

  private static boolean callable(Symbol symbol) {
    return symbol.kind() == SymbolKind.FUNCTION
        || symbol.kind() == SymbolKind.METHOD
        || symbol.kind() == SymbolKind.TYPE_METHOD
        || symbol.kind() == SymbolKind.TYPE;
  }

  private static int activeParameter(List<Token> tokens, int opening, Symbol callable) {
    if (callable.parameters().isEmpty()) return 0;
    int depth = 0;
    int ordinal = 0;
    int segmentStart = opening + 1;
    for (int index = opening + 1; index < tokens.size(); index++) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.LEFT_PAREN || kind == TokenKind.LEFT_BRACKET) depth++;
      if ((kind == TokenKind.RIGHT_PAREN || kind == TokenKind.RIGHT_BRACKET) && depth > 0) depth--;
      if (kind == TokenKind.COMMA && depth == 0) {
        ordinal++;
        segmentStart = index + 1;
      }
    }
    if (segmentStart + 1 < tokens.size()
        && tokens.get(segmentStart).kind() == TokenKind.IDENTIFIER
        && tokens.get(segmentStart + 1).kind() == TokenKind.COLON) {
      String label = tokens.get(segmentStart).lexeme();
      for (int index = 0; index < callable.parameters().size(); index++) {
        if (callable.parameters().get(index).name().equals(label)) return index;
      }
    }
    return Math.min(ordinal, callable.parameters().size() - 1);
  }
}
