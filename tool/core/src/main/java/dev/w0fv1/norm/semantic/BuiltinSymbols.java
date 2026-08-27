package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.value.DocumentId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuiltinSymbols {
  private final BuiltinCatalog catalog;
  private final java.util.Set<DocumentId> moduleEvaluationDocuments;

  public BuiltinSymbols() {
    this(java.util.Set.of());
  }

  public BuiltinSymbols(java.util.Set<DocumentId> moduleEvaluationDocuments) {
    catalog = BuiltinCatalog.standard();
    this.moduleEvaluationDocuments = java.util.Set.copyOf(moduleEvaluationDocuments);
  }

  public Map<SymbolId, Symbol> symbols() {
    SymbolId publisher = catalog.global("__publishModule").orElseThrow().symbol().id();
    Map<SymbolId, Symbol> symbols = new java.util.LinkedHashMap<>(catalog.symbols());
    symbols.remove(publisher);
    return java.util.Collections.unmodifiableMap(symbols);
  }

  public Map<SymbolId, List<SymbolId>> members() {
    return catalog.members();
  }

  public Optional<Symbol> global(String name) {
    if (name.equals("__publishModule")) return Optional.empty();
    return catalog.global(name).map(BuiltinCatalog.GlobalDefinition::symbol);
  }

  public List<Symbol> globals(String name) {
    if (name.equals("__publishModule")) return List.of();
    return catalog.globals(name).stream().map(BuiltinCatalog.GlobalDefinition::symbol).toList();
  }

  public List<Symbol> globals(String name, DocumentId document) {
    if (name.equals("__publishModule") && !moduleEvaluationDocuments.contains(document)) {
      return List.of();
    }
    return catalog.globals(name).stream().map(BuiltinCatalog.GlobalDefinition::symbol).toList();
  }

  public Optional<Symbol> type(String name) {
    return catalog.type(name).map(BuiltinCatalog.TypeDefinition::symbol);
  }

  public Optional<Symbol> member(SemanticType owner, String name) {
    return catalog.member(owner, name);
  }

  public List<Symbol> members(SemanticType owner, String name) {
    return catalog.members(owner, name);
  }

  public List<Symbol> typeMembers(String owner, String name) {
    return catalog.typeMembers(owner, name);
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
    return catalog.protocolConformances(type);
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
