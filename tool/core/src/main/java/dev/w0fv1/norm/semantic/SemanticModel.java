package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.syntax.Token;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import dev.w0fv1.norm.value.SourceLocation;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SemanticModel implements SemanticIndex {
  private final SourceFile source;
  private final Syntax.Program syntax;
  private final Map<SymbolId, Symbol> symbols;
  private final Map<SourceSpan, SymbolId> bindings;
  private final Map<SourceSpan, SemanticType> expressionTypes;
  private final Map<SourceSpan, ResolvedCall> resolvedCalls;
  private final Map<SourceSpan, List<SemanticType>> functionReferenceTypeArguments;
  private final Map<SourceSpan, ResolvedCall> resolvedCallsByCallee;
  private final Map<SourceSpan, SymbolId> resolvedCallees;
  private final Map<SourceSpan, ResolvedIteration> iterations;
  private final Map<SourceSpan, ResolvedIndex> indexes;
  private final Map<SymbolId, List<SymbolId>> members;
  private final Map<SymbolId, List<SymbolId>> aliasTargets;
  private final Map<SymbolId, List<SymbolId>> callableGroups;
  private final Map<SymbolId, Map<SymbolId, SymbolId>> witnesses;
  private final Map<String, SymbolId> typeSymbols;
  private final Map<String, List<SemanticType>> interfaceParents;
  private final List<SemanticScope> scopes;
  private final List<Diagnostic> diagnostics;
  private final List<ImportableSymbol> importableSymbols;
  private final CompilationScope scope;
  private final List<Token> tokens;
  private final SpanIndex<SymbolId> bindingIndex;
  private final SpanIndex<SymbolId> resolvedCalleeIndex;
  private final SpanIndex<SemanticType> typeIndex;
  private final ReferenceIndex authoringReferences;
  private final ReferenceIndex semanticReferences;

  public SemanticModel(
      SourceFile source,
      Syntax.Program syntax,
      Map<SymbolId, Symbol> symbols,
      Map<SourceSpan, SymbolId> bindings,
      Map<SourceSpan, SemanticType> expressionTypes,
      Map<SourceSpan, ResolvedCall> resolvedCalls,
      Map<SourceSpan, List<SemanticType>> functionReferenceTypeArguments,
      Map<SourceSpan, ResolvedIteration> iterations,
      Map<SourceSpan, ResolvedIndex> indexes,
      Map<SymbolId, List<SymbolId>> members,
      Map<SymbolId, List<SymbolId>> aliasTargets,
      Map<SymbolId, List<SymbolId>> callableGroups,
      Map<SymbolId, Map<SymbolId, SymbolId>> witnesses,
      Map<String, SymbolId> typeSymbols,
      Map<String, List<SemanticType>> interfaceParents,
      List<SemanticScope> scopes,
      List<Diagnostic> diagnostics,
      List<ImportableSymbol> importableSymbols,
      CompilationScope scope) {
    this.source = Objects.requireNonNull(source, "source");
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.symbols = Map.copyOf(symbols);
    this.bindings = Map.copyOf(bindings);
    this.expressionTypes = Map.copyOf(expressionTypes);
    this.resolvedCalls = Map.copyOf(resolvedCalls);
    Map<SourceSpan, List<SemanticType>> copiedFunctionReferenceArguments = new LinkedHashMap<>();
    functionReferenceTypeArguments.forEach(
        (span, arguments) -> copiedFunctionReferenceArguments.put(span, List.copyOf(arguments)));
    this.functionReferenceTypeArguments = Map.copyOf(copiedFunctionReferenceArguments);
    Map<SourceSpan, ResolvedCall> callsByCallee = new LinkedHashMap<>();
    this.resolvedCalls.values().forEach(call -> callsByCallee.put(call.calleeSpan(), call));
    this.resolvedCallsByCallee = Map.copyOf(callsByCallee);
    this.iterations = Map.copyOf(iterations);
    this.indexes = Map.copyOf(indexes);
    Map<SymbolId, List<SymbolId>> copiedMembers = new LinkedHashMap<>();
    members.forEach((owner, values) -> copiedMembers.put(owner, List.copyOf(values)));
    this.members = Map.copyOf(copiedMembers);
    Map<SymbolId, List<SymbolId>> copiedAliases = new LinkedHashMap<>();
    aliasTargets.forEach((alias, targets) -> copiedAliases.put(alias, List.copyOf(targets)));
    this.aliasTargets = Map.copyOf(copiedAliases);
    Map<SymbolId, List<SymbolId>> copiedGroups = new LinkedHashMap<>();
    callableGroups.forEach((symbol, group) -> copiedGroups.put(symbol, List.copyOf(group)));
    this.callableGroups = Map.copyOf(copiedGroups);
    Map<SymbolId, Map<SymbolId, SymbolId>> copiedWitnesses = new LinkedHashMap<>();
    witnesses.forEach((owner, values) -> copiedWitnesses.put(owner, Map.copyOf(values)));
    this.witnesses = Map.copyOf(copiedWitnesses);
    this.typeSymbols = Map.copyOf(typeSymbols);
    Map<String, List<SemanticType>> copiedParents = new LinkedHashMap<>();
    interfaceParents.forEach(
        (identity, values) -> copiedParents.put(identity, List.copyOf(values)));
    this.interfaceParents = Map.copyOf(copiedParents);
    this.scopes = List.copyOf(scopes);
    this.diagnostics = List.copyOf(diagnostics);
    this.importableSymbols = List.copyOf(importableSymbols);
    this.scope = Objects.requireNonNull(scope, "scope");
    this.tokens = List.of();
    this.bindingIndex = SpanIndex.from(this.bindings);
    Map<SourceSpan, SymbolId> callTargets = new LinkedHashMap<>();
    this.resolvedCalls.values().forEach(call -> callTargets.put(call.calleeSpan(), call.target()));
    this.resolvedCallees = Map.copyOf(callTargets);
    this.resolvedCalleeIndex = SpanIndex.from(this.resolvedCallees);
    this.typeIndex = SpanIndex.from(this.expressionTypes);
    this.authoringReferences = ReferenceIndex.from(this.bindings);
    this.semanticReferences =
        ReferenceIndex.semantic(this.bindings, this.aliasTargets, this.resolvedCalls);
  }

  private SemanticModel(
      SourceFile source, Syntax.Program syntax, List<Token> tokens, SemanticModel project) {
    this.source = Objects.requireNonNull(source, "source");
    this.syntax = Objects.requireNonNull(syntax, "syntax");
    this.symbols = project.symbols;
    this.bindings = project.bindings;
    this.expressionTypes = project.expressionTypes;
    this.resolvedCalls = project.resolvedCalls;
    this.functionReferenceTypeArguments = project.functionReferenceTypeArguments;
    this.resolvedCallsByCallee = project.resolvedCallsByCallee;
    this.resolvedCallees = project.resolvedCallees;
    this.iterations = project.iterations;
    this.indexes = project.indexes;
    this.members = project.members;
    this.aliasTargets = project.aliasTargets;
    this.callableGroups = project.callableGroups;
    this.witnesses = project.witnesses;
    this.typeSymbols = project.typeSymbols;
    this.interfaceParents = project.interfaceParents;
    this.scopes = project.scopes;
    this.diagnostics = project.diagnostics;
    this.importableSymbols = project.importableSymbols;
    this.scope = project.scope;
    this.tokens = List.copyOf(tokens);
    this.bindingIndex = project.bindingIndex;
    this.resolvedCalleeIndex = project.resolvedCalleeIndex;
    this.typeIndex = project.typeIndex;
    this.authoringReferences = project.authoringReferences;
    this.semanticReferences = project.semanticReferences;
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

  public SemanticContribution contribution(SourceSpan previousRoot, SourceFile currentSource) {
    Objects.requireNonNull(previousRoot, "previousRoot");
    Objects.requireNonNull(currentSource, "currentSource");
    if (!previousRoot.source().id().equals(currentSource.id())) {
      throw new IllegalArgumentException("semantic contribution must remain in one document");
    }
    Map<SourceSpan, SymbolId> selectedBindings = rebase(bindings, previousRoot, currentSource);
    Map<SourceSpan, SemanticType> selectedTypes =
        rebase(expressionTypes, previousRoot, currentSource);
    Map<SourceSpan, ResolvedCall> selectedCalls = new LinkedHashMap<>();
    resolvedCalls.forEach(
        (span, call) -> {
          if (!inside(span, previousRoot)) return;
          SourceSpan rebasedSpan = rebase(span, currentSource);
          SourceSpan rebasedCallee = rebase(call.calleeSpan(), currentSource);
          selectedCalls.put(
              rebasedSpan,
              new ResolvedCall(
                  call.kind(),
                  call.target(),
                  rebasedCallee,
                  call.arguments(),
                  call.parameters(),
                  call.callableTypeArguments(),
                  call.resultType()));
        });
    Map<SourceSpan, List<SemanticType>> selectedFunctionArguments =
        rebase(functionReferenceTypeArguments, previousRoot, currentSource);
    Map<SourceSpan, ResolvedIteration> selectedIterations =
        rebase(iterations, previousRoot, currentSource);
    Map<SourceSpan, ResolvedIndex> selectedIndexes = rebase(indexes, previousRoot, currentSource);
    Set<SymbolId> selectedIds = new java.util.LinkedHashSet<>(selectedBindings.values());
    selectedCalls.values().forEach(call -> selectedIds.add(call.target()));
    Map<SymbolId, Symbol> selectedSymbols = new LinkedHashMap<>();
    selectedIds.forEach(
        id -> {
          Symbol symbol = symbols.get(id);
          if (symbol != null) selectedSymbols.put(id, symbol);
        });
    List<SemanticScope> selectedScopes =
        scopes.stream()
            .filter(scope -> inside(scope.span(), previousRoot))
            .map(
                scope ->
                    new SemanticScope(
                        rebase(scope.span(), currentSource), scope.depth(), scope.symbols()))
            .toList();
    return new SemanticContribution(
        selectedSymbols,
        selectedBindings,
        selectedTypes,
        selectedCalls,
        selectedFunctionArguments,
        selectedIterations,
        selectedIndexes,
        selectedScopes);
  }

  public Set<SourceLocation> declarationDependencies(SourceSpan root) {
    Objects.requireNonNull(root, "root");
    Set<SymbolId> targets = new java.util.LinkedHashSet<>();
    bindings.forEach(
        (span, symbol) -> {
          if (inside(span, root)) targets.add(resolveAlias(symbol));
        });
    resolvedCalls.forEach(
        (span, call) -> {
          if (inside(span, root)) targets.add(call.target());
        });
    return targets.stream()
        .map(symbols::get)
        .filter(Objects::nonNull)
        .map(Symbol::declaration)
        .flatMap(Optional::stream)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static <T> Map<SourceSpan, T> rebase(
      Map<SourceSpan, T> values, SourceSpan root, SourceFile currentSource) {
    Map<SourceSpan, T> selected = new LinkedHashMap<>();
    values.forEach(
        (span, value) -> {
          if (inside(span, root)) selected.put(rebase(span, currentSource), value);
        });
    return Map.copyOf(selected);
  }

  private static boolean inside(SourceSpan span, SourceSpan root) {
    return span.source().id().equals(root.source().id())
        && span.startOffset() >= root.startOffset()
        && span.endOffset() <= root.endOffset();
  }

  private static SourceSpan rebase(SourceSpan span, SourceFile source) {
    return new SourceSpan(source, span.startOffset(), span.endOffset());
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

  public int nextSourceSymbolOrdinal() {
    int maximum = -1;
    for (SymbolId id : symbols.keySet()) {
      if (!id.value().startsWith("source/")) continue;
      int separator = id.value().lastIndexOf('#');
      if (separator < 0) continue;
      try {
        maximum = Math.max(maximum, Integer.parseInt(id.value().substring(separator + 1)));
      } catch (NumberFormatException ignored) {
        throw new IllegalStateException("source symbol id has an invalid ordinal");
      }
    }
    return maximum + 1;
  }

  public List<ImportableSymbol> importableSymbols(DocumentId importer) {
    Objects.requireNonNull(importer, "importer");
    return importableSymbols.stream()
        .filter(
            candidate ->
                candidate.symbol().declaration().isPresent()
                    && scope.canRead(
                        importer, candidate.symbol().declaration().orElseThrow().document()))
        .toList();
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
    SymbolId target = resolvedCallees.get(span);
    return Optional.ofNullable(target != null ? target : bindings.get(span))
        .map(this::resolveAlias)
        .map(symbols::get);
  }

  public Optional<Symbol> resolvedSymbolAt(int offset) {
    return resolvedSymbolAt(source.id(), offset);
  }

  public Optional<Symbol> resolvedSymbolAt(DocumentId document, int offset) {
    Optional<Symbol> callTarget =
        resolvedCalleeIndex.at(document, offset).map(SpanIndex.Entry::value).map(symbols::get);
    return callTarget.isPresent() ? callTarget : symbolAt(document, offset).map(this::resolveAlias);
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

  public Optional<SemanticType> typeOf(Syntax.TypeRef reference) {
    if (reference.name().equals("Function") && !reference.arguments().isEmpty()) {
      List<SemanticType> signature =
          reference.arguments().stream().map(this::typeOf).flatMap(Optional::stream).toList();
      if (signature.size() != reference.arguments().size()) return Optional.empty();
      SemanticType function =
          SemanticType.function(signature.getFirst(), signature.subList(1, signature.size()));
      return Optional.of(reference.nullable() ? function.nullable() : function);
    }
    Optional<Symbol> symbol = resolvedSymbolOf(reference.span());
    if (symbol.isEmpty()) return Optional.empty();
    SemanticType base = symbol.orElseThrow().type();
    SemanticType resolved = base;
    if (base.kind() != SemanticType.Kind.TYPE_PARAMETER && !reference.arguments().isEmpty()) {
      List<SemanticType> arguments =
          reference.arguments().stream().map(this::typeOf).flatMap(Optional::stream).toList();
      if (arguments.size() != reference.arguments().size()) return Optional.empty();
      resolved = SemanticType.declared(base.identity(), base.name(), arguments, base.category());
    }
    return Optional.of(reference.nullable() ? resolved.nullable() : resolved);
  }

  @Override
  public Optional<ResolvedCall> callOf(SourceSpan callSpan) {
    return Optional.ofNullable(resolvedCalls.get(callSpan));
  }

  public Optional<ResolvedCall> callAtCallee(SourceSpan calleeSpan) {
    return Optional.ofNullable(resolvedCallsByCallee.get(calleeSpan));
  }

  public List<SemanticType> functionReferenceTypeArguments(SourceSpan span) {
    return functionReferenceTypeArguments.getOrDefault(span, List.of());
  }

  public Optional<SymbolId> witness(SymbolId classType, SymbolId requirement) {
    return Optional.ofNullable(witnesses.getOrDefault(classType, Map.of()).get(requirement));
  }

  public Optional<ResolvedIteration> iterationOf(SourceSpan iterableSpan) {
    return Optional.ofNullable(iterations.get(iterableSpan));
  }

  public Optional<ResolvedIndex> indexOf(SourceSpan indexSpan) {
    return Optional.ofNullable(indexes.get(indexSpan));
  }

  public List<Symbol> members(SemanticType type) {
    return members(type, new java.util.HashSet<>());
  }

  private List<Symbol> members(SemanticType type, Set<String> visiting) {
    if (type.kind() == SemanticType.Kind.TYPE_PARAMETER) {
      Optional<SemanticType> upperBound =
          symbols.values().stream()
              .filter(
                  symbol ->
                      symbol.typeParameters().stream()
                          .anyMatch(
                              parameter -> parameter.type().identity().equals(type.identity())))
              .flatMap(symbol -> symbol.typeParameters().stream())
              .filter(parameter -> parameter.type().identity().equals(type.identity()))
              .map(TypeParameterInfo::upperBound)
              .flatMap(Optional::stream)
              .findFirst();
      if (upperBound.isPresent()) return members(upperBound.orElseThrow(), visiting);
    }
    if (!visiting.add(type.identity())) return List.of();
    Optional<Symbol> owner =
        Optional.ofNullable(typeSymbols.get(type.identity())).map(symbols::get);
    if (owner.isEmpty()) return List.of();
    List<Symbol> result = new java.util.ArrayList<>();
    members.getOrDefault(owner.orElseThrow().id(), List.of()).stream()
        .map(symbols::get)
        .filter(Objects::nonNull)
        .map(
            symbol ->
                symbol.id().value().startsWith("builtin/")
                    ? BuiltinCatalog.standard().member(type, symbol.id()).orElse(symbol)
                    : specializeMember(owner.orElseThrow(), type, symbol))
        .forEach(result::add);
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    if (owner.orElseThrow().typeParameters().size() == type.arguments().size()) {
      for (int index = 0; index < type.arguments().size(); index++) {
        substitutions.put(
            owner.orElseThrow().typeParameters().get(index).type().identity(),
            type.arguments().get(index));
      }
    }
    for (SemanticType parent : interfaceParents.getOrDefault(type.identity(), List.of())) {
      for (Symbol inherited : members(parent.substitute(substitutions), visiting)) {
        if (result.stream().noneMatch(existing -> existing.id().equals(inherited.id()))) {
          result.add(inherited);
        }
      }
    }
    return List.copyOf(result);
  }

  public List<Symbol> callableAlternatives(Symbol symbol) {
    List<SymbolId> targets = aliasTargets.get(symbol.id());
    boolean alias = targets != null;
    if (targets == null) targets = callableGroups.get(symbol.id());
    if (targets == null) return List.of(symbol);
    String presentedName = symbol.name();
    return targets.stream()
        .map(symbols::get)
        .filter(Objects::nonNull)
        .map(target -> alias ? withName(target, presentedName) : target)
        .toList();
  }

  public List<Symbol> typeMembers(String typeName) {
    return BuiltinCatalog.standard().type(typeName).stream()
        .flatMap(type -> type.typeMembers().stream())
        .map(dev.w0fv1.norm.builtin.BuiltinCatalog.MemberDefinition::symbol)
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
                    .forEach(
                        symbol -> {
                          if (callable(symbol)) {
                            boolean shadowed =
                                visible.values().stream()
                                    .anyMatch(
                                        candidate ->
                                            candidate.name().equals(symbol.name())
                                                && !callable(candidate));
                            if (!shadowed) visible.put(visibleKey(symbol), symbol);
                          } else {
                            visible
                                .entrySet()
                                .removeIf(entry -> entry.getValue().name().equals(symbol.name()));
                            visible.put(symbol.name(), symbol);
                          }
                        }));
    return List.copyOf(visible.values());
  }

  private static boolean callable(Symbol symbol) {
    return symbol.kind() == SymbolKind.FUNCTION
        || symbol.kind() == SymbolKind.METHOD
        || symbol.kind() == SymbolKind.INTERFACE_METHOD
        || symbol.kind() == SymbolKind.TYPE_METHOD;
  }

  private static String visibleKey(Symbol symbol) {
    if (!callable(symbol)) return symbol.name();
    return symbol.name()
        + "\u0000"
        + symbol.parameters().stream()
            .map(parameter -> parameter.type().displayName())
            .collect(java.util.stream.Collectors.joining(","));
  }

  public List<SourceSpan> references(SymbolId id) {
    return semanticReferences.references(id);
  }

  public List<SourceSpan> authoringReferences(SymbolId id) {
    return authoringReferences.references(id);
  }

  public boolean isAlias(SymbolId id) {
    return aliasTargets.containsKey(Objects.requireNonNull(id, "id"));
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

  private boolean contains(SourceSpan span, int offset) {
    return span.isEmpty()
        ? offset == span.startOffset()
        : span.startOffset() <= offset
            && (offset < span.endOffset()
                || offset == source.length() && span.endOffset() == source.length());
  }

  private SymbolId resolveAlias(SymbolId id) {
    SymbolId current = id;
    Set<SymbolId> visited = new java.util.HashSet<>();
    while (visited.add(current)) {
      List<SymbolId> targets = aliasTargets.get(current);
      if (targets == null || targets.size() != 1) return current;
      current = targets.getFirst();
    }
    return current;
  }

  private Symbol specializeMember(Symbol owner, SemanticType receiver, Symbol member) {
    if (owner.typeParameters().size() != receiver.arguments().size()) return member;
    Map<String, SemanticType> substitutions = new LinkedHashMap<>();
    for (int index = 0; index < owner.typeParameters().size(); index++) {
      TypeParameterInfo parameterInfo = owner.typeParameters().get(index);
      SemanticType argument = receiver.arguments().get(index);
      substitutions.put(parameterInfo.type().identity(), argument);
    }
    if (substitutions.isEmpty()) return member;
    return new Symbol(
        member.id(),
        member.name(),
        member.kind(),
        member.type().substitute(substitutions),
        member.declaration(),
        member.owner(),
        member.typeParameters(),
        member.parameters().stream()
            .map(
                parameter ->
                    new ParameterInfo(parameter.name(), parameter.type().substitute(substitutions)))
            .toList(),
        member.documentation());
  }

  private static Symbol withName(Symbol symbol, String name) {
    return new Symbol(
        symbol.id(),
        name,
        symbol.kind(),
        symbol.type(),
        symbol.declaration(),
        symbol.owner(),
        symbol.typeParameters(),
        symbol.parameters(),
        symbol.documentation());
  }
}
