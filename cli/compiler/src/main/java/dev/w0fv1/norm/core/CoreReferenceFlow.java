package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.LexicalLifetime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class CoreReferenceFlow {
  private final Deque<Scope> scopes = new ArrayDeque<>();
  private final Map<Integer, LexicalLifetime.Region> storageRegions = new LinkedHashMap<>();
  private final Map<Integer, LexicalLifetime> referenceLifetimes = new LinkedHashMap<>();
  private final Map<CoreExpression, LexicalLifetime> expressionLifetimes = new IdentityHashMap<>();
  private final Deque<Set<Integer>> writeCollectors = new ArrayDeque<>();

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

  void requireDeclared(int localIndex) {
    storageLifetime(localIndex);
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
    writeCollectors.forEach(writes -> writes.add(localIndex));
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

  Writes trackWrites() {
    Set<Integer> locals = new LinkedHashSet<>();
    writeCollectors.addFirst(locals);
    return new Writes(locals);
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

  static State overlay(State base, State writes, Set<Integer> writtenLocals) {
    Map<Integer, LexicalLifetime> result = new LinkedHashMap<>(base.referenceLifetimes());
    for (int local : writtenLocals) {
      LexicalLifetime lifetime = writes.referenceLifetimes().get(local);
      if (lifetime == null) result.remove(local);
      else result.put(local, lifetime);
    }
    return new State(result);
  }

  record State(Map<Integer, LexicalLifetime> referenceLifetimes) {
    State {
      referenceLifetimes = Map.copyOf(referenceLifetimes);
    }
  }

  final class Writes implements AutoCloseable {
    private final Set<Integer> locals;
    private boolean closed;

    private Writes(Set<Integer> locals) {
      this.locals = locals;
    }

    Set<Integer> locals() {
      return Set.copyOf(locals);
    }

    @Override
    public void close() {
      if (closed || writeCollectors.removeFirst() != locals) {
        throw new IllegalStateException("core reference write tracking is unbalanced");
      }
      closed = true;
    }
  }

  private record Scope(LexicalLifetime.Region region, List<Integer> locals) {}
}
