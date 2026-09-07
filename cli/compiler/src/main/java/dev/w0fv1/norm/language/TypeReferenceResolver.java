package dev.w0fv1.norm.language;

import dev.w0fv1.norm.frontend.TypeSyntaxParser;
import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.semantic.TypeApplication;
import dev.w0fv1.norm.semantic.TypeArguments;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class TypeReferenceResolver {
  Optional<List<SemanticType>> arguments(
      DocumentSemanticModel document, List<Token> tokens, int start, int end, int offset) {
    return TypeSyntaxParser.arguments(tokens.subList(start, end))
        .flatMap(
            references -> {
              List<SemanticType> arguments = new ArrayList<>();
              for (var reference : references) {
                Optional<SemanticType> type = resolve(document, reference, offset);
                if (type.isEmpty()) return Optional.empty();
                arguments.add(type.orElseThrow());
              }
              return Optional.of(List.copyOf(arguments));
            });
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
    return TypeSyntaxParser.type(tokens.subList(typeStart, typeEnd))
        .flatMap(reference -> resolve(document, reference, offset));
  }

  private Optional<SemanticType> resolve(
      DocumentSemanticModel document, Syntax.TypeRef reference, int offset) {
    Optional<SemanticType> known = document.semanticModel().typeOf(reference);
    if (known.isPresent()) return known.filter(type -> !type.equals(SemanticType.DYNAMIC));
    List<SemanticType> arguments = new ArrayList<>();
    for (var argument : reference.arguments()) {
      Optional<SemanticType> resolved = resolve(document, argument, offset);
      if (resolved.isEmpty()) return Optional.empty();
      arguments.add(resolved.orElseThrow());
    }
    SemanticType resolved =
        TypeApplication.resolve(
            reference,
            arguments,
            () -> {
              Optional<Symbol> symbol =
                  document.semanticModel().visibleSymbols(offset).stream()
                      .filter(
                          candidate ->
                              candidate.kind() == SymbolKind.TYPE
                                  || candidate.kind() == SymbolKind.INTERFACE
                                  || candidate.kind() == SymbolKind.TYPE_PARAMETER)
                      .filter(candidate -> candidate.name().equals(reference.name()))
                      .map(document.semanticModel()::resolveAlias)
                      .findFirst();
              if (symbol.isEmpty()) return SemanticType.DYNAMIC;
              Symbol declaration = symbol.orElseThrow();
              if (declaration.type().kind() == SemanticType.Kind.TYPE_PARAMETER)
                return declaration.type();
              return TypeArguments.complete(declaration.typeParameters(), arguments)
                  .map(
                      completed ->
                          SemanticType.declared(
                              declaration.type().identity(),
                              declaration.type().name(),
                              completed,
                              declaration.type().category()))
                  .orElse(SemanticType.DYNAMIC);
            });
    return resolved.equals(SemanticType.DYNAMIC) ? Optional.empty() : Optional.of(resolved);
  }
}
