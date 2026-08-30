package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class TypeReferenceResolver {
  Optional<List<SemanticType>> arguments(
      DocumentSemanticModel document, List<Token> tokens, int start, int end, int offset) {
    if (start >= end) return Optional.of(List.of());
    Cursor cursor = new Cursor(tokens.subList(start, end));
    if (!cursor.match(TokenKind.LESS)) return Optional.empty();
    List<SemanticType> arguments = new ArrayList<>();
    do {
      Optional<SemanticType> argument = parse(document, cursor, offset);
      if (argument.isEmpty()) return Optional.empty();
      arguments.add(argument.orElseThrow());
    } while (cursor.match(TokenKind.COMMA));
    if (!cursor.match(TokenKind.GREATER) || !cursor.atEnd()) return Optional.empty();
    return Optional.of(List.copyOf(arguments));
  }

  Optional<SemanticType> beforeIncompleteInitializer(DocumentSemanticModel document, int offset) {
    List<Token> tokens =
        document.tokens().stream()
            .filter(token -> token.kind() != TokenKind.END_OF_FILE)
            .filter(token -> token.span().endOffset() <= offset)
            .toList();
    int equalIndex = -1;
    for (int index = tokens.size() - 1; index >= 0; index--) {
      if (tokens.get(index).kind() == TokenKind.EQUAL) {
        equalIndex = index;
        break;
      }
    }
    if (equalIndex < 0 || tokens.size() - equalIndex > 2) return Optional.empty();
    int nameIndex = equalIndex - 1;
    if (nameIndex < 1 || tokens.get(nameIndex).kind() != TokenKind.IDENTIFIER) {
      return Optional.empty();
    }
    int typeEnd = nameIndex;
    int typeStart = typeEnd - 1;
    if (tokens.get(typeStart).kind() == TokenKind.QUESTION) typeStart--;
    if (typeStart < 0) return Optional.empty();
    if (tokens.get(typeStart).kind() == TokenKind.GREATER) {
      int depth = 0;
      while (typeStart >= 0) {
        TokenKind kind = tokens.get(typeStart).kind();
        if (kind == TokenKind.GREATER) depth++;
        if (kind == TokenKind.LESS && --depth == 0) {
          typeStart--;
          break;
        }
        typeStart--;
      }
    }
    if (typeStart < 0) return Optional.empty();
    Cursor cursor = new Cursor(tokens.subList(typeStart, typeEnd));
    Optional<SemanticType> type = parse(document, cursor, offset);
    return cursor.atEnd() ? type : Optional.empty();
  }

  private Optional<SemanticType> parse(DocumentSemanticModel document, Cursor cursor, int offset) {
    if (cursor.atEnd() || !typeToken(cursor.current().kind())) return Optional.empty();
    Token name = cursor.advance();
    List<SemanticType> arguments = new ArrayList<>();
    if (cursor.match(TokenKind.LESS)) {
      do {
        Optional<SemanticType> argument = parse(document, cursor, offset);
        if (argument.isEmpty()) return Optional.empty();
        arguments.add(argument.orElseThrow());
      } while (cursor.match(TokenKind.COMMA));
      if (!cursor.match(TokenKind.GREATER)) return Optional.empty();
    }
    boolean nullable = cursor.match(TokenKind.QUESTION);
    Optional<Symbol> symbol =
        document.semanticModel().visibleSymbols(offset).stream()
            .filter(
                candidate ->
                    candidate.kind() == SymbolKind.TYPE
                        || candidate.kind() == SymbolKind.INTERFACE
                        || candidate.kind() == SymbolKind.TYPE_PARAMETER)
            .filter(candidate -> candidate.name().equals(name.value()))
            .map(document.semanticModel()::resolveAlias)
            .findFirst();
    if (symbol.isEmpty()) return Optional.empty();
    SemanticType base = symbol.orElseThrow().type();
    SemanticType resolved =
        base.kind() == SemanticType.Kind.TYPE_PARAMETER || arguments.isEmpty()
            ? base
            : SemanticType.declared(base.identity(), base.name(), arguments, base.category());
    return Optional.of(nullable ? resolved.nullable() : resolved);
  }

  private static boolean typeToken(TokenKind kind) {
    return kind == TokenKind.IDENTIFIER;
  }

  private static final class Cursor {
    private final List<Token> tokens;
    private int index;

    private Cursor(List<Token> tokens) {
      this.tokens = List.copyOf(tokens);
    }

    private boolean atEnd() {
      return index == tokens.size();
    }

    private Token current() {
      return tokens.get(index);
    }

    private Token advance() {
      return tokens.get(index++);
    }

    private boolean match(TokenKind kind) {
      if (atEnd() || current().kind() != kind) return false;
      index++;
      return true;
    }
  }
}
