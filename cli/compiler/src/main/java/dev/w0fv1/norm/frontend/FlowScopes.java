package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticScope;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.value.LexicalLifetime;
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
  private final Map<SymbolId, LexicalLifetime> referenceLifetimes = new HashMap<>();
  private final Map<SymbolId, LexicalLifetime.Region> declarationRegions = new HashMap<>();
  private final List<SemanticScope> semanticScopes = new ArrayList<>();

  void clear() {
    scopes.clear();
    types.clear();
    referenceLifetimes.clear();
    declarationRegions.clear();
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
    LexicalLifetime.Region region =
        scopes.isEmpty() ? LexicalLifetime.Region.root() : scopes.getFirst().region().child();
    scopes.addFirst(new Scope(new HashMap<>(), new ArrayList<>(), span, scopes.size(), region));
  }

  void pop() {
    Scope scope = scopes.removeFirst();
    scope.declarations().forEach(types::remove);
    scope.declarations().forEach(referenceLifetimes::remove);
    scope.declarations().forEach(declarationRegions::remove);
    semanticScopes.add(new SemanticScope(scope.span(), scope.depth(), scope.declarations()));
  }

  boolean declare(String name, SemanticType type, SymbolId id) {
    Scope scope = scopes.getFirst();
    if (scope.symbols().putIfAbsent(name, new ScopedSymbol(type, id)) != null) return false;
    scope.declarations().add(id);
    types.put(id, type);
    declarationRegions.put(id, scope.region());
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

  LexicalLifetime storageLifetime(ScopedSymbol symbol) {
    return storageLifetime(symbol.id());
  }

  LexicalLifetime storageLifetime(SymbolId id) {
    return declarationRegions.get(id).lifetime();
  }

  LexicalLifetime currentLifetime() {
    return scopes.getFirst().region().lifetime();
  }

  LexicalLifetime referenceLifetime(ScopedSymbol symbol) {
    return referenceLifetimes.get(symbol.id());
  }

  void updateReferenceLifetime(ScopedSymbol symbol, LexicalLifetime lifetime) {
    referenceLifetimes.put(symbol.id(), lifetime);
  }

  FlowState snapshot() {
    return new FlowState(types, referenceLifetimes);
  }

  void replace(FlowState state) {
    types.clear();
    types.putAll(state.types());
    referenceLifetimes.clear();
    referenceLifetimes.putAll(state.referenceLifetimes());
  }

  record ScopedSymbol(SemanticType declaredType, SymbolId id) {}

  record FlowState(
      Map<SymbolId, SemanticType> types, Map<SymbolId, LexicalLifetime> referenceLifetimes) {
    FlowState {
      types = Map.copyOf(types);
      referenceLifetimes = Map.copyOf(referenceLifetimes);
    }
  }

  private record Scope(
      Map<String, ScopedSymbol> symbols,
      List<SymbolId> declarations,
      SourceSpan span,
      int depth,
      LexicalLifetime.Region region) {}
}
