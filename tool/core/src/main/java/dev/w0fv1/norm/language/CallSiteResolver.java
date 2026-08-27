package dev.w0fv1.norm.language;

import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.syntax.TokenKind;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    SemanticModel model = document.semanticModel();
    CandidateSet resolved = callables(document, model, tokens, nameIndex, offset);
    if (resolved.candidates().isEmpty()) return Optional.empty();
    Optional<List<dev.w0fv1.norm.semantic.SemanticType>> parsedArguments =
        typeReferences.arguments(document, tokens, nameIndex + 1, opening, offset);
    boolean explicitArguments = nameIndex + 1 < opening;
    if (explicitArguments && parsedArguments.isEmpty()) return Optional.empty();
    List<dev.w0fv1.norm.semantic.SemanticType> arguments = parsedArguments.orElse(List.of());
    List<Symbol> candidates = resolved.candidates();
    if (explicitArguments) {
      candidates =
          candidates.stream()
              .filter(candidate -> candidate.typeParameters().size() == arguments.size())
              .toList();
    }
    candidates =
        candidates.stream().map(candidate -> symbols.specialize(candidate, arguments)).toList();
    Set<String> labels = argumentLabels(tokens, opening);
    if (!labels.isEmpty()) {
      List<Symbol> labeled =
          candidates.stream()
              .filter(
                  candidate ->
                      candidate.parameters().stream()
                          .map(dev.w0fv1.norm.semantic.ParameterInfo::name)
                          .collect(java.util.stream.Collectors.toSet())
                          .containsAll(labels))
              .toList();
      if (!labeled.isEmpty()) candidates = labeled;
    }
    if (candidates.isEmpty()) return Optional.empty();
    int activeSignature = 0;
    if (resolved.preferred().isPresent()) {
      for (int index = 0; index < candidates.size(); index++) {
        if (candidates.get(index).id().equals(resolved.preferred().orElseThrow().id())) {
          activeSignature = index;
          break;
        }
      }
    }
    Symbol active = candidates.get(activeSignature);
    return Optional.of(
        new CallSite(candidates, activeSignature, activeParameter(tokens, opening, active)));
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

  private CandidateSet callables(
      DocumentSemanticModel document,
      SemanticModel model,
      List<Token> tokens,
      int nameIndex,
      int offset) {
    Token name = tokens.get(nameIndex);
    Optional<ResolvedCall> exactCall = model.callAtCallee(name.span());
    if (exactCall.isPresent()) {
      ResolvedCall call = exactCall.orElseThrow();
      if (call.kind() == ResolvedCall.Kind.INVOKE) {
        Optional<Symbol> functionValue = model.symbolOf(name.span());
        if (functionValue.isPresent()) {
          Symbol source = functionValue.orElseThrow();
          Symbol callable =
              new Symbol(
                  source.id(),
                  source.name(),
                  SymbolKind.FUNCTION,
                  call.resultType(),
                  source.declaration(),
                  source.owner(),
                  List.of(),
                  call.parameters(),
                  source.documentation());
          return new CandidateSet(List.of(callable), Optional.of(callable));
        }
      }
      Optional<Symbol> target = model.symbol(call.target());
      if (target.isPresent()) {
        Symbol declaration = SymbolPresentation.annotation(model, target.orElseThrow());
        String presentedName =
            model.symbolOf(name.span()).map(Symbol::name).orElse(declaration.name());
        List<dev.w0fv1.norm.semantic.SemanticType> typeArguments =
            declaration.kind() == SymbolKind.TYPE || declaration.kind() == SymbolKind.INTERFACE
                ? call.resultType().arguments()
                : call.callableTypeArguments();
        List<dev.w0fv1.norm.semantic.TypeParameterInfo> instantiatedTypeParameters =
            java.util.stream.IntStream.range(0, typeArguments.size())
                .mapToObj(
                    index ->
                        new dev.w0fv1.norm.semantic.TypeParameterInfo(
                            index < declaration.typeParameters().size()
                                ? declaration.typeParameters().get(index).name()
                                : typeArguments.get(index).displayName(),
                            typeArguments.get(index)))
                .toList();
        Symbol instantiated =
            new Symbol(
                declaration.id(),
                presentedName,
                declaration.kind(),
                call.resultType(),
                declaration.declaration(),
                declaration.owner(),
                instantiatedTypeParameters,
                call.parameters(),
                declaration.documentation());
        return new CandidateSet(List.of(instantiated), Optional.of(instantiated));
      }
    }
    Optional<Symbol> bound = model.resolvedSymbolOf(name.span()).filter(CallSiteResolver::callable);
    if (bound.isPresent()) {
      if (model.annotations().schema(bound.orElseThrow().id()).isPresent()) {
        Symbol annotation = SymbolPresentation.annotation(model, bound.orElseThrow());
        return new CandidateSet(List.of(annotation), Optional.of(annotation));
      }
      List<Symbol> candidates =
          model.callableAlternatives(bound.orElseThrow()).stream()
              .map(SymbolPresentation::callable)
              .toList();
      return new CandidateSet(
          candidates, Optional.of(SymbolPresentation.callable(bound.orElseThrow())));
    }
    if (nameIndex >= 2
        && (tokens.get(nameIndex - 1).kind() == TokenKind.DOT
            || tokens.get(nameIndex - 1).kind() == TokenKind.QUESTION_DOT)) {
      int receiverNameIndex = receiverName(tokens, nameIndex - 1);
      if (receiverNameIndex < 0) return new CandidateSet(List.of(), Optional.empty());
      Token receiverToken = tokens.get(receiverNameIndex);
      int receiverOffset = receiverToken.span().startOffset();
      Optional<Symbol> receiver =
          model
              .symbolAt(receiverOffset)
              .or(
                  () ->
                      model.visibleSymbols(offset).stream()
                          .filter(symbol -> symbol.name().equals(receiverToken.value()))
                          .findFirst());
      if (receiver.isPresent() && receiver.orElseThrow().kind() == SymbolKind.TYPE) {
        Symbol type = model.resolveAlias(receiver.orElseThrow());
        Optional<List<dev.w0fv1.norm.semantic.SemanticType>> arguments =
            typeReferences.arguments(
                document, tokens, receiverNameIndex + 1, nameIndex - 1, offset);
        dev.w0fv1.norm.semantic.SemanticType receiverType = type.type();
        if (arguments.isPresent() && !arguments.orElseThrow().isEmpty()) {
          receiverType =
              dev.w0fv1.norm.semantic.SemanticType.declared(
                  type.type().identity(),
                  type.type().name(),
                  arguments.orElseThrow(),
                  type.type().category());
        }
        List<Symbol> sourceMembers =
            model.members(receiverType).stream()
                .filter(symbol -> symbol.name().equals(name.value()))
                .toList();
        if (!sourceMembers.isEmpty()) {
          return new CandidateSet(sourceMembers, Optional.empty());
        }
        return new CandidateSet(
            model.typeMembers(type.name()).stream()
                .filter(symbol -> symbol.name().equals(name.value()))
                .toList(),
            Optional.empty());
      }
      Optional<dev.w0fv1.norm.semantic.SemanticType> receiverType =
          model
              .typeOf(receiverToken.span())
              .or(() -> model.typeAt(receiverOffset))
              .or(() -> receiver.map(Symbol::type));
      return new CandidateSet(
          receiverType.stream()
              .flatMap(type -> model.members(type).stream())
              .filter(symbol -> symbol.name().equals(name.value()))
              .toList(),
          Optional.empty());
    }
    List<Symbol> visible =
        model.visibleSymbols(offset).stream()
            .filter(symbol -> symbol.name().equals(name.value()))
            .map(symbol -> SymbolPresentation.annotation(model, symbol))
            .flatMap(
                symbol ->
                    model.annotations().schema(symbol.id()).isPresent()
                        ? java.util.stream.Stream.of(symbol)
                        : model.callableAlternatives(symbol).stream())
            .filter(CallSiteResolver::callable)
            .map(SymbolPresentation::callable)
            .toList();
    if (!visible.isEmpty()) return new CandidateSet(unique(visible), Optional.empty());
    return new CandidateSet(
        model.symbols().stream()
            .filter(symbol -> symbol.name().equals(name.value()))
            .filter(symbol -> symbol.owner().isEmpty())
            .filter(
                symbol ->
                    symbol.declaration().isEmpty()
                        || symbol
                            .declaration()
                            .orElseThrow()
                            .document()
                            .equals(model.source().id()))
            .map(symbol -> SymbolPresentation.annotation(model, symbol))
            .filter(CallSiteResolver::callable)
            .map(SymbolPresentation::callable)
            .toList(),
        Optional.empty());
  }

  private static int receiverName(List<Token> tokens, int dot) {
    int index = dot - 1;
    if (index >= 0 && tokens.get(index).kind() == TokenKind.GREATER) {
      int depth = 0;
      while (index >= 0) {
        TokenKind kind = tokens.get(index).kind();
        if (kind == TokenKind.GREATER) depth++;
        if (kind == TokenKind.LESS && --depth == 0) return index - 1;
        index--;
      }
      return -1;
    }
    return index;
  }

  private static List<Symbol> unique(List<Symbol> symbols) {
    LinkedHashMap<dev.w0fv1.norm.semantic.SymbolId, Symbol> result = new LinkedHashMap<>();
    symbols.forEach(symbol -> result.putIfAbsent(symbol.id(), symbol));
    return List.copyOf(result.values());
  }

  private static Set<String> argumentLabels(List<Token> tokens, int opening) {
    Set<String> labels = new java.util.LinkedHashSet<>();
    int depth = 0;
    for (int index = opening + 1; index + 1 < tokens.size(); index++) {
      TokenKind kind = tokens.get(index).kind();
      if (kind == TokenKind.LEFT_PAREN || kind == TokenKind.LEFT_BRACKET) depth++;
      if ((kind == TokenKind.RIGHT_PAREN || kind == TokenKind.RIGHT_BRACKET) && depth > 0) depth--;
      if (depth == 0
          && kind == TokenKind.IDENTIFIER
          && tokens.get(index + 1).kind() == TokenKind.COLON) {
        labels.add(tokens.get(index).value());
      }
    }
    return Set.copyOf(labels);
  }

  private static boolean callable(Symbol symbol) {
    return symbol.kind() == SymbolKind.FUNCTION
        || symbol.kind() == SymbolKind.METHOD
        || symbol.kind() == SymbolKind.INTERFACE_METHOD
        || symbol.kind() == SymbolKind.TYPE_METHOD
        || symbol.kind() == SymbolKind.TYPE
        || symbol.kind() == SymbolKind.INTERFACE
        || symbol.kind() == SymbolKind.ENUM_VARIANT
        || symbol.type().isFunction();
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
      String label = tokens.get(segmentStart).value();
      for (int index = 0; index < callable.parameters().size(); index++) {
        if (callable.parameters().get(index).name().equals(label)) return index;
      }
    }
    return Math.min(ordinal, callable.parameters().size() - 1);
  }

  private record CandidateSet(List<Symbol> candidates, Optional<Symbol> preferred) {
    private CandidateSet {
      candidates = List.copyOf(candidates);
    }
  }
}
