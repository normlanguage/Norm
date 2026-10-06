package dev.w0fv1.norm.builtin;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.semantic.BuiltinSemanticIndex;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.source.DocumentId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuiltinSymbols implements BuiltinSemanticIndex {
  private final BuiltinCatalog catalog;
  private final java.util.Set<DocumentId> moduleEvaluationDocuments;
  private final java.util.Set<DocumentId> standardLibraryDocuments;
  private final java.util.Set<DocumentId> bindingDocuments;
  private final List<dev.w0fv1.norm.semantic.BuiltinTypeConformance> typeConformances;

  public BuiltinSymbols() {
    this(java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
  }

  public BuiltinSymbols(java.util.Set<DocumentId> moduleEvaluationDocuments) {
    this(moduleEvaluationDocuments, java.util.Set.of(), java.util.Set.of());
  }

  public BuiltinSymbols(
      java.util.Set<DocumentId> moduleEvaluationDocuments,
      java.util.Set<DocumentId> standardLibraryDocuments) {
    this(moduleEvaluationDocuments, standardLibraryDocuments, java.util.Set.of());
  }

  public BuiltinSymbols(
      java.util.Set<DocumentId> moduleEvaluationDocuments,
      java.util.Set<DocumentId> standardLibraryDocuments,
      java.util.Set<DocumentId> bindingDocuments) {
    this(moduleEvaluationDocuments, standardLibraryDocuments, bindingDocuments, List.of());
  }

  public BuiltinSymbols(
      java.util.Set<DocumentId> moduleEvaluationDocuments,
      java.util.Set<DocumentId> standardLibraryDocuments,
      java.util.Set<DocumentId> bindingDocuments,
      List<dev.w0fv1.norm.semantic.BuiltinTypeConformance> typeConformances) {
    catalog = BuiltinCatalog.standard();
    this.moduleEvaluationDocuments = java.util.Set.copyOf(moduleEvaluationDocuments);
    this.standardLibraryDocuments = java.util.Set.copyOf(standardLibraryDocuments);
    this.bindingDocuments = java.util.Set.copyOf(bindingDocuments);
    this.typeConformances = typeConformances.stream().distinct().toList();
  }

  public Map<SymbolId, Symbol> symbols() {
    Map<SymbolId, Symbol> symbols = new java.util.LinkedHashMap<>(catalog.symbols());
    symbols.entrySet().removeIf(entry -> entry.getValue().name().startsWith("__"));
    return java.util.Collections.unmodifiableMap(symbols);
  }

  public Map<SymbolId, List<SymbolId>> members() {
    return catalog.members();
  }

  public Optional<Symbol> global(String name) {
    if (name.startsWith("__")) return Optional.empty();
    return catalog.global(name).map(BuiltinCatalog.GlobalDefinition::symbol);
  }

  public List<Symbol> globals(String name) {
    if (name.startsWith("__")) return List.of();
    return catalog.globals(name).stream().map(BuiltinCatalog.GlobalDefinition::symbol).toList();
  }

  public List<Symbol> globals(String name, DocumentId document) {
    if (name.equals("__publishModule"))
      return moduleEvaluationDocuments.contains(document)
          ? catalog.globals(name).stream().map(BuiltinCatalog.GlobalDefinition::symbol).toList()
          : List.of();
    if (name.startsWith("__jarInvoke"))
      return bindingDocuments.contains(document)
          ? catalog.globals(name).stream().map(BuiltinCatalog.GlobalDefinition::symbol).toList()
          : List.of();
    if (name.startsWith("__") && !standardLibraryDocuments.contains(document)) return List.of();
    return catalog.globals(name).stream().map(BuiltinCatalog.GlobalDefinition::symbol).toList();
  }

  public Optional<Symbol> type(String name) {
    return catalog.type(name).map(BuiltinCatalog.TypeDefinition::symbol);
  }

  public Optional<Symbol> member(SemanticType owner, String name) {
    return catalog.member(owner, name);
  }

  @Override
  public Optional<Symbol> member(SemanticType owner, SymbolId member) {
    return catalog.member(owner, member);
  }

  public List<Symbol> members(SemanticType owner, String name) {
    return catalog.members(owner, name);
  }

  public List<Symbol> typeMembers(String owner, String name) {
    return catalog.typeMembers(owner, name);
  }

  @Override
  public List<Symbol> typeMembers(String owner) {
    return catalog.type(owner).stream()
        .flatMap(type -> type.typeMembers().stream())
        .map(BuiltinCatalog.MemberDefinition::symbol)
        .toList();
  }

  public boolean isType(String name) {
    return catalog.type(name).isPresent();
  }

  public int typeArity(String name) {
    return catalog.type(name).map(BuiltinCatalog.TypeDefinition::arity).orElse(-1);
  }

  public SemanticType instantiate(String name, List<SemanticType> arguments) {
    return catalog.instantiate(name, arguments);
  }

  public Optional<BuiltinCatalog.ResolvedIterable> resolveIterable(SemanticType type) {
    return catalog.resolveIterable(type);
  }

  public List<SemanticType> protocolConformances(SemanticType type) {
    return java.util.stream.Stream.concat(
            catalog.protocolConformances(type).stream(),
            typeConformances.stream()
                .filter(value -> value.concreteType().equals(type.nonNullable()))
                .map(dev.w0fv1.norm.semantic.BuiltinTypeConformance::interfaceType))
        .distinct()
        .toList();
  }

  @Override
  public List<dev.w0fv1.norm.semantic.BuiltinTypeConformance> typeConformances() {
    return typeConformances;
  }

  public Optional<BuiltinCatalog.ResolvedIndex> resolveIndex(SemanticType type) {
    return catalog.resolveIndex(type);
  }

  public Optional<List<ParameterInfo>> constructorParameters(SemanticType type) {
    return catalog.constructorParameters(type);
  }

  public Optional<IntrinsicId> collectionLiteral(SemanticType type) {
    return catalog.collectionLiteral(type);
  }

  public Optional<IntrinsicId> intrinsic(SymbolId symbol) {
    return catalog.intrinsic(symbol);
  }

  public Optional<BuiltinCatalog.ResolvedCollectionLiteral> resolveCollectionLiteral(
      SemanticType expected) {
    return catalog.resolveCollectionLiteral(expected);
  }
}
