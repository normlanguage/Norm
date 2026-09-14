package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.semantic.ImportableSymbol;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

record DeclarationAnalysis(
    Map<SymbolId, Symbol> symbols,
    Map<SymbolId, DeclarationContract> contracts,
    Map<SymbolId, List<SymbolId>> callableGroups,
    Map<String, List<SemanticType>> interfaceParents,
    List<ImportableSymbol> importableSymbols,
    List<Diagnostic> diagnostics) {
  DeclarationAnalysis {
    symbols = Map.copyOf(symbols);
    contracts = Map.copyOf(contracts);
    callableGroups = immutableLists(callableGroups);
    interfaceParents = immutableLists(interfaceParents);
    importableSymbols = List.copyOf(importableSymbols);
    diagnostics = List.copyOf(diagnostics);
  }

  java.util.Set<dev.w0fv1.norm.source.DocumentId> sourceDocuments() {
    return contracts.keySet().stream()
        .map(symbols::get)
        .flatMap(symbol -> symbol.declaration().stream())
        .map(dev.w0fv1.norm.source.SourceLocation::document)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  Map<dev.w0fv1.norm.source.SourceSpan, Map<SymbolId, DeclarationContract>> contracts(
      List<dev.w0fv1.norm.source.SourceSpan> roots) {
    var index =
        dev.w0fv1.norm.semantic.SpanIndex.of(
            roots.stream()
                .map(root -> new dev.w0fv1.norm.semantic.SpanIndex.Entry<>(root, root))
                .toList());
    var selected =
        new java.util.LinkedHashMap<
            dev.w0fv1.norm.source.SourceSpan, Map<SymbolId, DeclarationContract>>();
    roots.forEach(root -> selected.put(root, new java.util.LinkedHashMap<>()));
    contracts.forEach(
        (id, contract) ->
            symbols
                .get(id)
                .declaration()
                .ifPresent(
                    location ->
                        index
                            .at(location.document(), location.startOffset())
                            .ifPresent(root -> selected.get(root.value()).put(id, contract))));
    return selected.entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
  }

  Map<dev.w0fv1.norm.value.ModuleCoordinate, Map<SymbolId, DeclarationContract>> moduleContracts(
      dev.w0fv1.norm.value.CompilationScope scope,
      java.util.Set<dev.w0fv1.norm.value.ModuleCoordinate> modules) {
    var result =
        new java.util.LinkedHashMap<
            dev.w0fv1.norm.value.ModuleCoordinate, Map<SymbolId, DeclarationContract>>();
    modules.forEach(module -> result.put(module, new java.util.LinkedHashMap<>()));
    contracts.forEach(
        (id, contract) ->
            symbols
                .get(id)
                .declaration()
                .ifPresent(
                    location -> {
                      var selected = result.get(scope.coordinate(location.document()).module());
                      if (selected != null) selected.put(id, contract);
                    }));
    result.replaceAll((module, values) -> Map.copyOf(values));
    return Map.copyOf(result);
  }

  private static <K, V> Map<K, List<V>> immutableLists(Map<K, List<V>> values) {
    return values.entrySet().stream()
        .collect(
            Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
  }
}
