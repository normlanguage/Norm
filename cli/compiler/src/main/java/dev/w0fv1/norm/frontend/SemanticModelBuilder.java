package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.builtin.BuiltinSymbols;
import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.semantic.AnnotationApplication;
import dev.w0fv1.norm.semantic.AnnotationIndex;
import dev.w0fv1.norm.semantic.AnnotationSchema;
import dev.w0fv1.norm.semantic.ImportableSymbol;
import dev.w0fv1.norm.semantic.ResolvedCall;
import dev.w0fv1.norm.semantic.ResolvedIndex;
import dev.w0fv1.norm.semantic.ResolvedIteration;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticScope;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationScope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SemanticModelBuilder {
  private final CompilationScope scope;
  private int nextSymbolId;
  private final AnalysisJournal journal = new AnalysisJournal();
  private final Map<String, SymbolId> copyMethods = new HashMap<>();
  private final Map<Syntax.ImportDecl, SymbolId> importAliases = new IdentityHashMap<>();

  SymbolId copyMethod(String type) {
    return copyMethods.get(type);
  }

  void putCopyMethod(String type, SymbolId symbol) {
    journal.put(copyMethods, type, symbol);
  }

  Map<Syntax.ImportDecl, SymbolId> importAliases() {
    return Collections.unmodifiableMap(importAliases);
  }

  private final Map<SymbolId, Symbol> symbols = new LinkedHashMap<>();
  private final Map<SourceSpan, SymbolId> bindings = new LinkedHashMap<>();
  private final java.util.Set<SourceSpan> declarationOperators = new java.util.LinkedHashSet<>();
  private final Map<SourceSpan, SemanticType> semanticTypes = new LinkedHashMap<>();
  private final Map<SourceSpan, SemanticType> resultBuilders = new LinkedHashMap<>();

  void putResultBuilder(SourceSpan span, SemanticType type) {
    journal.put(resultBuilders, span, type);
  }

  private final Map<SourceSpan, ResolvedCall> resolvedCalls = new LinkedHashMap<>();
  private final Map<SourceSpan, List<SemanticType>> functionReferenceTypeArguments =
      new LinkedHashMap<>();
  private final Map<SourceSpan, ResolvedIteration> iterations = new LinkedHashMap<>();
  private final Map<SourceSpan, ResolvedIndex> indexes = new LinkedHashMap<>();
  private final Map<SymbolId, List<SymbolId>> members = new LinkedHashMap<>();
  private final Map<SymbolId, List<SymbolId>> aliasTargets = new LinkedHashMap<>();
  private final Map<SymbolId, Map<SymbolId, SymbolId>> witnesses = new LinkedHashMap<>();
  private final Map<String, SemanticType> aggregateParents = new LinkedHashMap<>();
  private final Map<SymbolId, SymbolId> methodOverrides = new LinkedHashMap<>();
  private final Map<String, SymbolId> typeSymbols = new LinkedHashMap<>();
  private final Map<Object, SymbolId> declarationSymbols = new IdentityHashMap<>();
  private final Map<SymbolId, AnnotationSchema> annotationSchemas = new LinkedHashMap<>();
  private final List<AnnotationApplication> annotationApplications = new ArrayList<>();

  SemanticModelBuilder(BuiltinSymbols builtins, CompilationScope scope) {
    this.scope = java.util.Objects.requireNonNull(scope, "scope");
    journal.putAll(symbols, builtins.symbols());
    symbols.values().stream()
        .filter(symbol -> symbol.kind() == SymbolKind.TYPE)
        .forEach(symbol -> journal.put(typeSymbols, symbol.type().identity(), symbol.id()));
    builtins.members().forEach((owner, values) -> journal.put(members, owner, List.copyOf(values)));
  }

  int nextSymbolId() {
    return nextSymbolId;
  }

  void reserveIds(int minimum) {
    nextSymbolId = Math.max(nextSymbolId, minimum);
  }

  SymbolId allocate(DocumentId document) {
    return SymbolId.source(scope.coordinate(document), nextSymbolId++);
  }

  void imports(ImportResolver.Result imported) {
    journal.putAll(importAliases, imported.importAliases());
    journal.putAll(symbols, imported.aliases());
    journal.putAll(bindings, imported.bindings());
    journal.putAll(aliasTargets, imported.aliasTargets());
  }

  void reuse(SemanticContribution contribution) {
    journal.putAll(symbols, contribution.symbols());
    journal.putAll(bindings, contribution.bindings());
    contribution.declarationOperators().forEach(value -> journal.add(declarationOperators, value));
    journal.putAll(semanticTypes, contribution.expressionTypes());
    journal.putAll(resultBuilders, contribution.resultBuilders());
    journal.putAll(resolvedCalls, contribution.resolvedCalls());
    journal.putAll(functionReferenceTypeArguments, contribution.functionReferenceTypeArguments());
    journal.putAll(iterations, contribution.iterations());
    journal.putAll(indexes, contribution.indexes());
  }

  void addMember(SymbolId owner, SymbolId member) {
    var values = new ArrayList<>(members.getOrDefault(owner, List.of()));
    values.add(member);
    journal.put(members, owner, List.copyOf(values));
  }

  void putWitness(SymbolId owner, SymbolId requirement, SymbolId implementation) {
    var values = new LinkedHashMap<>(witnesses.getOrDefault(owner, Map.of()));
    values.put(requirement, implementation);
    journal.put(witnesses, owner, Map.copyOf(values));
  }

  Symbol symbolOf(Object declaration) {
    return java.util.Objects.requireNonNull(
        symbols.get(declarationSymbols.get(declaration)), "declaration symbol");
  }

  Map<SymbolId, Symbol> symbols() {
    return Collections.unmodifiableMap(symbols);
  }

  void putSymbol(SymbolId key, Symbol value) {
    journal.put(symbols, key, value);
  }

  void putSymbolIfAbsent(SymbolId key, Symbol value) {
    journal.putIfAbsent(symbols, key, value);
  }

  Map<SourceSpan, SymbolId> bindings() {
    return Collections.unmodifiableMap(bindings);
  }

  void putBinding(SourceSpan key, SymbolId value) {
    journal.put(bindings, key, value);
  }

  void putDeclarationOperator(SourceSpan key, SymbolId value) {
    journal.put(bindings, key, value);
    journal.add(declarationOperators, key);
  }

  Map<SourceSpan, SemanticType> semanticTypes() {
    return Collections.unmodifiableMap(semanticTypes);
  }

  void putType(SourceSpan key, SemanticType value) {
    journal.put(semanticTypes, key, value);
  }

  void putCall(SourceSpan key, ResolvedCall value) {
    journal.put(resolvedCalls, key, value);
  }

  void putFunctionReference(SourceSpan key, List<SemanticType> value) {
    journal.put(functionReferenceTypeArguments, key, List.copyOf(value));
  }

  void putIteration(SourceSpan key, ResolvedIteration value) {
    journal.put(iterations, key, value);
  }

  void putIndex(SourceSpan key, ResolvedIndex value) {
    journal.put(indexes, key, value);
  }

  void putAggregateParent(String key, SemanticType value) {
    journal.put(aggregateParents, key, value);
  }

  void putOverride(SymbolId key, SymbolId value) {
    journal.put(methodOverrides, key, value);
  }

  SymbolId overriddenMethod(SymbolId method) {
    return methodOverrides.get(method);
  }

  void putTypeSymbol(String key, SymbolId value) {
    journal.putIfAbsent(typeSymbols, key, value);
  }

  Map<Object, SymbolId> declarationSymbols() {
    return Collections.unmodifiableMap(declarationSymbols);
  }

  void putDeclaration(Object key, SymbolId value) {
    journal.put(declarationSymbols, key, value);
  }

  Map<SymbolId, AnnotationSchema> annotationSchemas() {
    return Collections.unmodifiableMap(annotationSchemas);
  }

  void putAnnotationSchema(SymbolId key, AnnotationSchema value) {
    journal.put(annotationSchemas, key, value);
  }

  List<AnnotationApplication> annotationApplications() {
    return List.copyOf(annotationApplications);
  }

  void addAnnotation(AnnotationApplication application) {
    journal.add(annotationApplications, application);
  }

  SemanticModel build(
      Syntax.Program syntax,
      CompilationScope scope,
      BuiltinSymbols builtins,
      Map<SymbolId, List<SymbolId>> callableGroups,
      Map<String, List<SemanticType>> interfaceParents,
      List<SemanticScope> scopes,
      List<Diagnostic> diagnostics,
      List<ImportableSymbol> importableSymbols) {
    return new SemanticModel(
        syntax.span().source(),
        syntax,
        symbols,
        bindings,
        declarationOperators,
        semanticTypes,
        resultBuilders,
        resolvedCalls,
        functionReferenceTypeArguments,
        iterations,
        indexes,
        members,
        aliasTargets,
        callableGroups,
        witnesses,
        aggregateParents,
        methodOverrides,
        typeSymbols,
        interfaceParents,
        new AnnotationIndex(annotationSchemas, annotationApplications),
        scopes,
        diagnostics,
        importableSymbols,
        scope,
        builtins);
  }

  Checkpoint checkpoint() {
    return new Checkpoint(nextSymbolId, journal.checkpoint());
  }

  void restore(Checkpoint checkpoint) {
    journal.restore(checkpoint.journal());
    nextSymbolId = checkpoint.nextSymbolId();
  }

  record Checkpoint(int nextSymbolId, AnalysisJournal.Checkpoint journal) {}
}
