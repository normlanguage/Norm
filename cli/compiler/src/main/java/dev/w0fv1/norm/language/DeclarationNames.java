package dev.w0fv1.norm.language;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

final class DeclarationNames {
  private final CompilationSnapshot snapshot;
  private final Map<SymbolId, String> names = new HashMap<>();
  private final Map<SymbolId, String> selectors = new HashMap<>();

  DeclarationNames(CompilationSnapshot snapshot) {
    this.snapshot = snapshot;
  }

  String name(Symbol symbol, boolean signature) {
    var cache = signature ? selectors : names;
    String cached = cache.get(symbol.id());
    if (cached != null) return cached;
    String prefix =
        symbol
            .owner()
            .flatMap(snapshot.semanticModel()::symbol)
            .map(owner -> name(owner, signature))
            .orElseGet(
                () ->
                    symbol
                        .declaration()
                        .flatMap(location -> snapshot.document(location.document()))
                        .map(document -> document.syntax().packageName())
                        .orElse(""));
    String result = (prefix.isEmpty() ? "" : prefix + ".") + symbol.name();
    if (signature
        && switch (symbol.kind()) {
          case FUNCTION, METHOD, TYPE_METHOD, INTERFACE_METHOD, CONSTRUCTOR, EXTENSION -> true;
          default -> false;
        }) {
      result +=
          symbol.parameters().stream()
              .map(parameter -> parameter.type().displayName())
              .collect(Collectors.joining(",", "(", ")"));
    }
    cache.put(symbol.id(), result);
    return result;
  }
}
