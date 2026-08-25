package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticScope;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FlowScopes {
  private final Deque<Scope> scopes = new ArrayDeque<>();
  private final Map<SymbolId, SemanticType> types = new HashMap<>();
  private final List<SemanticScope> semanticScopes = new ArrayList<>();

  void clear() {
    scopes.clear();
    types.clear();
  }

  void addSemanticScope(SemanticScope scope) {
    semanticScopes.add(scope);
  }

  List<SemanticScope> semanticScopes() {
    return List.copyOf(semanticScopes);
  }

  int semanticScopeCount() {
    return semanticScopes.size();
  }

  void restoreSemanticScopes(int size) {
    semanticScopes.subList(size, semanticScopes.size()).clear();
  }

  void push(SourceSpan span) {
    scopes.addFirst(new Scope(new HashMap<>(), new ArrayList<>(), span, scopes.size()));
  }

  void pop() {
    Scope scope = scopes.removeFirst();
    scope.declarations().forEach(types::remove);
    semanticScopes.add(new SemanticScope(scope.span(), scope.depth(), scope.declarations()));
  }

  boolean declare(String name, SemanticType type, SymbolId id) {
    Scope scope = scopes.getFirst();
    if (scope.symbols().putIfAbsent(name, new ScopedSymbol(type, id)) != null) return false;
    scope.declarations().add(id);
    types.put(id, type);
    return true;
  }

  ScopedSymbol find(String name) {
    for (Scope scope : scopes) {
      ScopedSymbol symbol = scope.symbols().get(name);
      if (symbol != null) return symbol;
    }
    return null;
  }

  SemanticType type(ScopedSymbol symbol) {
    return types.getOrDefault(symbol.id(), symbol.declaredType());
  }

  void update(ScopedSymbol symbol, SemanticType type) {
    types.put(symbol.id(), type);
  }

  Map<SymbolId, SemanticType> snapshot() {
    return new HashMap<>(types);
  }

  void replace(Map<SymbolId, SemanticType> values) {
    types.clear();
    types.putAll(values);
  }

  record ScopedSymbol(SemanticType declaredType, SymbolId id) {}

  private record Scope(
      Map<String, ScopedSymbol> symbols, List<SymbolId> declarations, SourceSpan span, int depth) {}
}
