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
  private int nextSymbolId;
  private final Map<String, SymbolId> copyMethods = new HashMap<>();
  private final Map<Syntax.ImportDecl, SymbolId> importAliases = new IdentityHashMap<>();

  SymbolId copyMethod(String type) {
    return copyMethods.get(type);
  }

  void putCopyMethod(String type, SymbolId symbol) {
    copyMethods.put(type, symbol);
  }

  Map<Syntax.ImportDecl, SymbolId> importAliases() {
    return Collections.unmodifiableMap(importAliases);
  }

  private final Map<SymbolId, Symbol> symbols = new LinkedHashMap<>();
  private final Map<SourceSpan, SymbolId> bindings = new LinkedHashMap<>();
  private final Map<SourceSpan, SemanticType> semanticTypes = new LinkedHashMap<>();
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

  SemanticModelBuilder(BuiltinSymbols builtins) {
    symbols.putAll(builtins.symbols());
    symbols.values().stream()
        .filter(symbol -> symbol.kind() == SymbolKind.TYPE)
        .forEach(symbol -> typeSymbols.put(symbol.type().identity(), symbol.id()));
    builtins.members().forEach((owner, values) -> members.put(owner, List.copyOf(values)));
  }

  private SemanticModelBuilder() {}

  int nextSymbolId() {
    return nextSymbolId;
  }

  void reserveIds(int minimum) {
    nextSymbolId = Math.max(nextSymbolId, minimum);
  }

  SymbolId allocate(DocumentId document) {
    return SymbolId.source(document, nextSymbolId++);
  }

  void imports(ImportResolver.Result imported) {
    importAliases.putAll(imported.importAliases());
    symbols.putAll(imported.aliases());
    bindings.putAll(imported.bindings());
    aliasTargets.putAll(imported.aliasTargets());
    reserveIds(imported.nextSymbolId());
  }

  void reuse(SemanticContribution contribution) {
    symbols.putAll(contribution.symbols());
    bindings.putAll(contribution.bindings());
    semanticTypes.putAll(contribution.expressionTypes());
    resolvedCalls.putAll(contribution.resolvedCalls());
    functionReferenceTypeArguments.putAll(contribution.functionReferenceTypeArguments());
    iterations.putAll(contribution.iterations());
    indexes.putAll(contribution.indexes());
  }

  void addMember(SymbolId owner, SymbolId member) {
    var values = new ArrayList<>(members.getOrDefault(owner, List.of()));
    values.add(member);
    members.put(owner, List.copyOf(values));
  }

  void putWitness(SymbolId owner, SymbolId requirement, SymbolId implementation) {
    var values = new LinkedHashMap<>(witnesses.getOrDefault(owner, Map.of()));
    values.put(requirement, implementation);
    witnesses.put(owner, Map.copyOf(values));
  }

  Map<SymbolId, Symbol> symbols() {
    return Collections.unmodifiableMap(symbols);
  }

  void putSymbol(SymbolId key, Symbol value) {
    symbols.put(key, value);
  }

  void putSymbolIfAbsent(SymbolId key, Symbol value) {
    symbols.putIfAbsent(key, value);
  }

  Map<SourceSpan, SymbolId> bindings() {
    return Collections.unmodifiableMap(bindings);
  }

  void putBinding(SourceSpan key, SymbolId value) {
    bindings.put(key, value);
  }

  Map<SourceSpan, SemanticType> semanticTypes() {
    return Collections.unmodifiableMap(semanticTypes);
  }

  void putType(SourceSpan key, SemanticType value) {
    semanticTypes.put(key, value);
  }

  void putCall(SourceSpan key, ResolvedCall value) {
    resolvedCalls.put(key, value);
  }

  void putFunctionReference(SourceSpan key, List<SemanticType> value) {
    functionReferenceTypeArguments.put(key, List.copyOf(value));
  }

  void putIteration(SourceSpan key, ResolvedIteration value) {
    iterations.put(key, value);
  }

  void putIndex(SourceSpan key, ResolvedIndex value) {
    indexes.put(key, value);
  }

  void putAggregateParent(String key, SemanticType value) {
    aggregateParents.put(key, value);
  }

  void putOverride(SymbolId key, SymbolId value) {
    methodOverrides.put(key, value);
  }

  void putTypeSymbol(String key, SymbolId value) {
    typeSymbols.putIfAbsent(key, value);
  }

  Map<Object, SymbolId> declarationSymbols() {
    return Collections.unmodifiableMap(declarationSymbols);
  }

  void putDeclaration(Object key, SymbolId value) {
    declarationSymbols.put(key, value);
  }

  Map<SymbolId, AnnotationSchema> annotationSchemas() {
    return Collections.unmodifiableMap(annotationSchemas);
  }

  void putAnnotationSchema(SymbolId key, AnnotationSchema value) {
    annotationSchemas.put(key, value);
  }

  List<AnnotationApplication> annotationApplications() {
    return List.copyOf(annotationApplications);
  }

  void addAnnotation(AnnotationApplication application) {
    annotationApplications.add(application);
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
        semanticTypes,
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
    var captured = new SemanticModelBuilder();
    captured.restore(this);
    return new Checkpoint(captured);
  }

  void restore(Checkpoint checkpoint) {
    restore(checkpoint.captured);
  }

  private void restore(SemanticModelBuilder captured) {
    nextSymbolId = captured.nextSymbolId;
    copyMethods.clear();
    copyMethods.putAll(captured.copyMethods);
    importAliases.clear();
    importAliases.putAll(captured.importAliases);
    symbols.clear();
    symbols.putAll(captured.symbols);
    bindings.clear();
    bindings.putAll(captured.bindings);
    semanticTypes.clear();
    semanticTypes.putAll(captured.semanticTypes);
    resolvedCalls.clear();
    resolvedCalls.putAll(captured.resolvedCalls);
    functionReferenceTypeArguments.clear();
    functionReferenceTypeArguments.putAll(captured.functionReferenceTypeArguments);
    iterations.clear();
    iterations.putAll(captured.iterations);
    indexes.clear();
    indexes.putAll(captured.indexes);
    members.clear();
    members.putAll(captured.members);
    aliasTargets.clear();
    aliasTargets.putAll(captured.aliasTargets);
    witnesses.clear();
    witnesses.putAll(captured.witnesses);
    aggregateParents.clear();
    aggregateParents.putAll(captured.aggregateParents);
    methodOverrides.clear();
    methodOverrides.putAll(captured.methodOverrides);
    typeSymbols.clear();
    typeSymbols.putAll(captured.typeSymbols);
    declarationSymbols.clear();
    declarationSymbols.putAll(captured.declarationSymbols);
    annotationSchemas.clear();
    annotationSchemas.putAll(captured.annotationSchemas);
    annotationApplications.clear();
    annotationApplications.addAll(captured.annotationApplications);
  }

  static final class Checkpoint {
    private final SemanticModelBuilder captured;

    private Checkpoint(SemanticModelBuilder captured) {
      this.captured = captured;
    }
  }
}
