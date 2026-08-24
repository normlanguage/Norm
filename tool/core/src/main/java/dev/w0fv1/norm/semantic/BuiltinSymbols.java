package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.builtin.IntrinsicId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BuiltinSymbols {
  private final BuiltinCatalog catalog;

  public BuiltinSymbols() {
    catalog = BuiltinCatalog.standard();
  }

  public Map<SymbolId, Symbol> symbols() {
    return catalog.symbols();
  }

  public Map<SymbolId, List<SymbolId>> members() {
    return catalog.members();
  }

  public Optional<Symbol> global(String name) {
    return catalog.global(name).map(BuiltinCatalog.GlobalDefinition::symbol);
  }

  public List<Symbol> globals(String name) {
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

  public Optional<BuiltinCatalog.ResolvedCollectionLiteral> resolveCollectionLiteral(
      SemanticType expected) {
    return catalog.resolveCollectionLiteral(expected);
  }
}
