package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class SemanticModel implements SemanticIndex {
  private final SourceFile source;
  private final Syntax.Program syntax;
  private final Map<SymbolId, Symbol> symbols;
  private final Map<SourceSpan, SymbolId> bindings;
  private final Map<SourceSpan, SemanticType> expressionTypes;
  private final Map<SourceSpan, ArgumentBinding> argumentBindings;
  private final Map<SourceSpan, List<SemanticType>> callTypeArguments;
  private final Map<SourceSpan, ResolvedIteration> iterations;
  private final Map<SourceSpan, ResolvedIndex> indexes;
  private final Map<SymbolId, List<SymbolId>> members;
  private final Map<SymbolId, SymbolId> aliasTargets;
  private final List<SemanticScope> scopes;
  private final List<Diagnostic> diagnostics;
  private final List<Token> tokens;
  private final SpanIndex<SymbolId> bindingIndex;
  private final SpanIndex<SemanticType> typeIndex;
  private final ReferenceIndex referenceIndex;

  public SemanticModel(
      SourceFile source,
      Syntax.Program syntax,
      Map<SymbolId, Symbol> symbols,
      Map<SourceSpan, SymbolId> bindings,
      Map<SourceSpan, SemanticType> expressionTypes,
      Map<SourceSpan, ArgumentBinding> argumentBindings,
      Map<SourceSpan, List<SemanticType>> callTypeArguments,
      Map<SourceSpan, ResolvedIteration> iterations,
      Map<SourceSpan, ResolvedIndex> indexes,
      Map<SymbolId, List<SymbolId>> members,
      Map<SymbolId, SymbolId> aliasTargets,
      List<SemanticScope> scopes,
      List<Diagnostic> diagnostics) {
    this.source = Objects.requireNonNull(source, "source");
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.symbols = Map.copyOf(symbols);
    this.bindings = Map.copyOf(bindings);
    this.expressionTypes = Map.copyOf(expressionTypes);
    this.argumentBindings = Map.copyOf(argumentBindings);
    Map<SourceSpan, List<SemanticType>> copiedCallTypeArguments = new LinkedHashMap<>();
    callTypeArguments.forEach(
        (span, arguments) -> copiedCallTypeArguments.put(span, List.copyOf(arguments)));
    this.callTypeArguments = Map.copyOf(copiedCallTypeArguments);
    this.iterations = Map.copyOf(iterations);
    this.indexes = Map.copyOf(indexes);
    Map<SymbolId, List<SymbolId>> copiedMembers = new LinkedHashMap<>();
    members.forEach((owner, values) -> copiedMembers.put(owner, List.copyOf(values)));
    this.members = Map.copyOf(copiedMembers);
    this.aliasTargets = Map.copyOf(aliasTargets);
    this.scopes = List.copyOf(scopes);
    this.diagnostics = List.copyOf(diagnostics);
    this.tokens = List.of();
    this.bindingIndex = SpanIndex.from(this.bindings);
    this.typeIndex = SpanIndex.from(this.expressionTypes);
    this.referenceIndex = ReferenceIndex.from(this.bindings, this.aliasTargets);
  }

  private SemanticModel(
      SourceFile source, Syntax.Program syntax, List<Token> tokens, SemanticModel project) {
    this.source = Objects.requireNonNull(source, "source");
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.symbols = project.symbols;
    this.bindings = project.bindings;
    this.expressionTypes = project.expressionTypes;
    this.argumentBindings = project.argumentBindings;
    this.callTypeArguments = project.callTypeArguments;
    this.iterations = project.iterations;
    this.indexes = project.indexes;
    this.members = project.members;
    this.aliasTargets = project.aliasTargets;
    this.scopes = project.scopes;
    this.diagnostics = project.diagnostics;
    this.tokens = List.copyOf(tokens);
    this.bindingIndex = project.bindingIndex;
    this.typeIndex = project.typeIndex;
    this.referenceIndex = project.referenceIndex;
  }

  public SemanticModel documentView(SourceFile source, Syntax.Program syntax) {
    return documentView(source, syntax, List.of());
  }

  public SemanticModel documentView(SourceFile source, Syntax.Program syntax, List<Token> tokens) {
    if (source.id().equals(this.source.id())
        && syntax == this.syntax
        && tokens.equals(this.tokens)) {
      return this;
    }
    return new SemanticModel(source, syntax, tokens, this);
  }

  public List<Token> tokens() {
    return tokens;
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
    return symbolAt(source.id(), offset);
  }

  public Optional<Symbol> symbolAt(DocumentId document, int offset) {
    return bindingIndex.at(document, offset).map(SpanIndex.Entry::value).map(symbols::get);
  }

  public Optional<SourceSpan> referenceAt(int offset) {
    return bindingIndex.at(source.id(), offset).map(SpanIndex.Entry::span);
  }

  public Optional<Symbol> symbolOf(SourceSpan span) {
    return Optional.ofNullable(bindings.get(span)).map(symbols::get);
  }

  public Optional<Symbol> resolvedSymbolOf(SourceSpan span) {
    return Optional.ofNullable(bindings.get(span)).map(this::resolveAlias).map(symbols::get);
  }

  public Symbol resolveAlias(Symbol symbol) {
    return symbols.get(resolveAlias(symbol.id()));
  }

  public Optional<SemanticType> typeAt(int offset) {
    return typeAt(source.id(), offset);
  }

  public Optional<SemanticType> typeAt(DocumentId document, int offset) {
    return typeIndex.at(document, offset).map(SpanIndex.Entry::value);
  }

  public Optional<SemanticType> typeOf(SourceSpan span) {
    return Optional.ofNullable(expressionTypes.get(span));
  }

  public Optional<ArgumentBinding> argumentsOf(SourceSpan callSpan) {
    return Optional.ofNullable(argumentBindings.get(callSpan));
  }

  public List<SemanticType> typeArgumentsOf(SourceSpan callSpan) {
    return callTypeArguments.getOrDefault(callSpan, List.of());
  }

  public Optional<ResolvedIteration> iterationOf(SourceSpan iterableSpan) {
    return Optional.ofNullable(iterations.get(iterableSpan));
  }

  public Optional<ResolvedIndex> indexOf(SourceSpan indexSpan) {
    return Optional.ofNullable(indexes.get(indexSpan));
  }

  public List<Symbol> members(SemanticType type) {
    Optional<Symbol> owner =
        symbols.values().stream()
            .filter(symbol -> symbol.kind() == SymbolKind.TYPE)
            .filter(symbol -> symbol.name().equals(type.name()))
            .findFirst();
    if (owner.isEmpty()) return List.of();
    return members.getOrDefault(owner.orElseThrow().id(), List.of()).stream()
        .map(symbols::get)
        .filter(Objects::nonNull)
        .map(
            symbol ->
                symbol.id().value().startsWith("builtin/")
                    ? BuiltinCatalog.standard().member(type, symbol.name()).orElse(symbol)
                    : symbol)
        .toList();
  }

  public List<Symbol> visibleSymbols(int offset) {
    Map<String, Symbol> visible = new LinkedHashMap<>();
    scopes.stream()
        .filter(scope -> scope.span().source().id().equals(source.id()))
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
    return referenceIndex.references(id);
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

  private SymbolId resolveAlias(SymbolId id) {
    SymbolId current = id;
    while (aliasTargets.containsKey(current)) current = aliasTargets.get(current);
    return current;
  }
}
