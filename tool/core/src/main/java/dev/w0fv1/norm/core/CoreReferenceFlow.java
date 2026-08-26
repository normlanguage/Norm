package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.LexicalLifetime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CoreReferenceFlow {
  private final Deque<Scope> scopes = new ArrayDeque<>();
  private final Map<Integer, LexicalLifetime.Region> storageRegions = new LinkedHashMap<>();
  private final Map<Integer, LexicalLifetime> referenceLifetimes = new LinkedHashMap<>();
  private final Map<CoreExpression, LexicalLifetime> expressionLifetimes = new IdentityHashMap<>();

  void push() {
    LexicalLifetime.Region region =
        scopes.isEmpty() ? LexicalLifetime.Region.root() : scopes.getFirst().region().child();
    scopes.addFirst(new Scope(region, new ArrayList<>()));
  }

  void pop() {
    Scope scope = scopes.removeFirst();
    scope.locals().forEach(storageRegions::remove);
    scope.locals().forEach(referenceLifetimes::remove);
  }

  void declare(int localIndex) {
    if (storageRegions.putIfAbsent(localIndex, scopes.getFirst().region()) != null) {
      throw new IllegalArgumentException("core local storage is declared more than once");
    }
    scopes.getFirst().locals().add(localIndex);
  }

  LexicalLifetime currentLifetime() {
    return scopes.getFirst().region().lifetime();
  }

  LexicalLifetime storageLifetime(int localIndex) {
    LexicalLifetime.Region region = storageRegions.get(localIndex);
    if (region == null) {
      throw new IllegalArgumentException("core local storage is outside its lexical region");
    }
    return region.lifetime();
  }

  LexicalLifetime referenceLifetime(int localIndex) {
    LexicalLifetime lifetime = referenceLifetimes.get(localIndex);
    if (lifetime == null) {
      throw new IllegalArgumentException("core reference local is not initialized");
    }
    return lifetime;
  }

  void update(int localIndex, LexicalLifetime lifetime) {
    storageLifetime(localIndex);
    referenceLifetimes.put(localIndex, Objects.requireNonNull(lifetime, "lifetime"));
  }

  LexicalLifetime expressionLifetime(CoreExpression expression) {
    return expressionLifetimes.get(expression);
  }

  void recordExpressionLifetime(CoreExpression expression, LexicalLifetime lifetime) {
    expressionLifetimes.put(
        Objects.requireNonNull(expression, "expression"),
        Objects.requireNonNull(lifetime, "lifetime"));
  }

  State snapshot() {
    return new State(referenceLifetimes);
  }

  void replace(State state) {
    referenceLifetimes.clear();
    referenceLifetimes.putAll(state.referenceLifetimes());
  }

  static State merge(State incoming, State left, State right) {
    Map<Integer, LexicalLifetime> result = new LinkedHashMap<>();
    for (Map.Entry<Integer, LexicalLifetime> entry : incoming.referenceLifetimes().entrySet()) {
      LexicalLifetime leftLifetime =
          left.referenceLifetimes().getOrDefault(entry.getKey(), entry.getValue());
      LexicalLifetime rightLifetime =
          right.referenceLifetimes().getOrDefault(entry.getKey(), entry.getValue());
      result.put(entry.getKey(), leftLifetime.narrowest(rightLifetime));
    }
    return new State(result);
  }

  record State(Map<Integer, LexicalLifetime> referenceLifetimes) {
    State {
      referenceLifetimes = Map.copyOf(referenceLifetimes);
    }
  }

  private record Scope(LexicalLifetime.Region region, List<Integer> locals) {}
}
