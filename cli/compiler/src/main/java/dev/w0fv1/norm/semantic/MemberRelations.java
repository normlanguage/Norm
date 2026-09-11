package dev.w0fv1.norm.semantic;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MemberRelations {
  private record PropertyIdentity(SymbolId owner, String name) {}

  private static final Comparator<Symbol> ORDER =
      Comparator.comparing(
              (Symbol symbol) ->
                  symbol
                      .declaration()
                      .map(location -> location.document().uri().toString())
                      .orElse(""))
          .thenComparingInt(
              symbol -> symbol.declaration().map(location -> location.startOffset()).orElse(-1));
  private final Map<SymbolId, Symbol> symbols;
  private final Map<SymbolId, Set<SymbolId>> parents;
  private final Map<SymbolId, Set<SymbolId>> neighbors;

  MemberRelations(
      Map<SymbolId, Symbol> symbols,
      Map<SymbolId, Map<SymbolId, SymbolId>> witnesses,
      Map<SymbolId, SymbolId> overrides) {
    this.symbols = symbols;
    var directed = new LinkedHashMap<SymbolId, Set<SymbolId>>();
    overrides.forEach(
        (member, parent) ->
            directed.computeIfAbsent(member, ignored -> new LinkedHashSet<>()).add(parent));
    witnesses
        .values()
        .forEach(
            table ->
                table.forEach(
                    (requirement, implementation) ->
                        directed
                            .computeIfAbsent(implementation, ignored -> new LinkedHashSet<>())
                            .add(requirement)));
    var connected = new LinkedHashMap<SymbolId, Set<SymbolId>>();
    directed.forEach(
        (member, targets) ->
            targets.forEach(
                target -> {
                  connected.computeIfAbsent(member, ignored -> new LinkedHashSet<>()).add(target);
                  connected.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(member);
                }));
    var properties = new LinkedHashMap<PropertyIdentity, Set<SymbolId>>();
    for (Symbol symbol : symbols.values()) {
      if (symbol.accessor() == Symbol.Accessor.NONE) continue;
      var identity = new PropertyIdentity(symbol.owner().orElseThrow(), symbol.name());
      properties.computeIfAbsent(identity, ignored -> new LinkedHashSet<>()).add(symbol.id());
    }
    properties
        .values()
        .forEach(
            accessors ->
                accessors.forEach(
                    accessor ->
                        connected
                            .computeIfAbsent(accessor, ignored -> new LinkedHashSet<>())
                            .addAll(accessors)));
    parents =
        directed.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    neighbors =
        connected.entrySet().stream()
            .collect(
                java.util.stream.Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
  }

  List<Symbol> parents(Symbol symbol) {
    return parents.getOrDefault(symbol.id(), Set.of()).stream()
        .map(symbols::get)
        .filter(java.util.Objects::nonNull)
        .sorted(ORDER)
        .toList();
  }

  List<Symbol> related(Symbol symbol) {
    var visited = new LinkedHashSet<SymbolId>();
    var pending = new ArrayDeque<SymbolId>();
    pending.add(symbol.id());
    while (!pending.isEmpty()) {
      SymbolId current = pending.removeFirst();
      if (visited.add(current)) pending.addAll(neighbors.getOrDefault(current, Set.of()));
    }
    return visited.stream()
        .map(symbols::get)
        .filter(java.util.Objects::nonNull)
        .sorted(ORDER)
        .toList();
  }
}
