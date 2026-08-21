package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SemanticModel {
  private final SourceFile source;
  private final Syntax.Program syntax;
  private final Map<SymbolId, Symbol> symbols;
  private final Map<SourceSpan, SymbolId> bindings;
  private final Map<SourceSpan, SemanticType> expressionTypes;
  private final Map<SourceSpan, ArgumentBinding> argumentBindings;
  private final Map<SymbolId, List<SymbolId>> members;
  private final List<SemanticScope> scopes;
  private final List<Diagnostic> diagnostics;

  public SemanticModel(
      SourceFile source,
      Syntax.Program syntax,
      Map<SymbolId, Symbol> symbols,
      Map<SourceSpan, SymbolId> bindings,
      Map<SourceSpan, SemanticType> expressionTypes,
      Map<SourceSpan, ArgumentBinding> argumentBindings,
      Map<SymbolId, List<SymbolId>> members,
      List<SemanticScope> scopes,
      List<Diagnostic> diagnostics) {
    this.source = Objects.requireNonNull(source, "source");
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.symbols = Map.copyOf(symbols);
    this.bindings = Map.copyOf(bindings);
    this.expressionTypes = Map.copyOf(expressionTypes);
    this.argumentBindings = Map.copyOf(argumentBindings);
    Map<SymbolId, List<SymbolId>> copiedMembers = new LinkedHashMap<>();
    members.forEach((owner, values) -> copiedMembers.put(owner, List.copyOf(values)));
    this.members = Map.copyOf(copiedMembers);
    this.scopes = List.copyOf(scopes);
    this.diagnostics = List.copyOf(diagnostics);
  }

  public SourceFile source() {
    return source;
  }

  public Syntax.Program syntax() {
    return syntax;
  }

  public List<Diagnostic> diagnostics() {
    return diagnostics;
  }

  public Optional<Symbol> symbol(SymbolId id) {
    return Optional.ofNullable(symbols.get(id));
  }

  public List<Symbol> symbols() {
    return List.copyOf(symbols.values());
  }

  public Optional<Symbol> symbolAt(int offset) {
    return bindingAt(offset).map(Map.Entry::getValue).map(symbols::get);
  }

  public Optional<SourceSpan> referenceAt(int offset) {
    return bindingAt(offset).map(Map.Entry::getKey);
  }

  private Optional<Map.Entry<SourceSpan, SymbolId>> bindingAt(int offset) {
    return bindings.entrySet().stream()
        .filter(entry -> contains(entry.getKey(), offset))
        .min(Comparator.comparingInt(entry -> entry.getKey().length()));
  }

  public Optional<Symbol> symbolOf(SourceSpan span) {
    return Optional.ofNullable(bindings.get(span)).map(symbols::get);
  }

  public Optional<SemanticType> typeAt(int offset) {
    return expressionTypes.entrySet().stream()
        .filter(entry -> contains(entry.getKey(), offset))
        .min(Comparator.comparingInt(entry -> entry.getKey().length()))
        .map(Map.Entry::getValue);
  }

  public Optional<SemanticType> typeOf(SourceSpan span) {
    return Optional.ofNullable(expressionTypes.get(span));
  }

  public Optional<ArgumentBinding> argumentsOf(SourceSpan callSpan) {
    return Optional.ofNullable(argumentBindings.get(callSpan));
  }

  public List<Symbol> members(SemanticType type) {
    Optional<Symbol> owner =
        symbols.values().stream()
            .filter(symbol -> symbol.kind() == SymbolKind.TYPE)
            .filter(symbol -> symbol.name().equals(type.displayName()))
            .findFirst();
    if (owner.isEmpty()) return List.of();
    return members.getOrDefault(owner.orElseThrow().id(), List.of()).stream()
        .map(symbols::get)
        .filter(Objects::nonNull)
        .toList();
  }

  public List<Symbol> visibleSymbols(int offset) {
    Map<String, Symbol> visible = new LinkedHashMap<>();
    symbols.values().stream()
        .filter(symbol -> symbol.owner().isEmpty())
        .forEach(symbol -> visible.put(symbol.name(), symbol));
    scopes.stream()
        .filter(scope -> contains(scope.span(), offset))
        .sorted(Comparator.comparingInt(SemanticScope::depth))
        .forEach(
            scope ->
                scope.symbols().stream()
                    .map(symbols::get)
                    .filter(Objects::nonNull)
                    .filter(
                        symbol ->
                            symbol.declaration().isEmpty()
                                || symbol.declaration().orElseThrow().startOffset() <= offset)
                    .forEach(symbol -> visible.put(symbol.name(), symbol)));
    return List.copyOf(visible.values());
  }

  public List<SourceSpan> references(SymbolId id) {
    List<SourceSpan> result = new ArrayList<>();
    bindings.forEach(
        (span, symbol) -> {
          if (symbol.equals(id)) result.add(span);
        });
    result.sort(Comparator.comparingInt(SourceSpan::startOffset));
    return List.copyOf(result);
  }

  public boolean hasRenameConflict(SymbolId id, String newName) {
    Symbol target = symbols.get(id);
    if (target == null) throw new IllegalArgumentException("unknown symbol id");
    if (target.name().equals(newName)) return false;
    if (target.kind() == SymbolKind.LOCAL_VARIABLE || target.kind() == SymbolKind.PARAMETER) {
      return scopes.stream()
          .filter(scope -> scope.symbols().contains(id))
          .flatMap(scope -> scope.symbols().stream())
          .filter(other -> !other.equals(id))
          .map(symbols::get)
          .filter(Objects::nonNull)
          .anyMatch(symbol -> symbol.name().equals(newName));
    }
    return symbols.values().stream()
        .filter(symbol -> !symbol.id().equals(id))
        .filter(symbol -> symbol.kind() == target.kind())
        .filter(symbol -> symbol.owner().equals(target.owner()))
        .anyMatch(symbol -> symbol.name().equals(newName));
  }

  private static boolean contains(SourceSpan span, int offset) {
    return span.isEmpty()
        ? offset == span.startOffset()
        : span.startOffset() <= offset && offset < span.endOffset();
  }
}
