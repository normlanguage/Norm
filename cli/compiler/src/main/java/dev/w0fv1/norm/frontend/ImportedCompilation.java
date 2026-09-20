package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundLocalId;
import dev.w0fv1.norm.core.CoreDefinitionOrigin;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.TokenSpanMapping;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record ImportedCompilation(
    Map<SourceSpan, SemanticContribution> contributions,
    Map<SourceSpan, TokenSpanMapping> mappings,
    Map<CoreBuildHistory.Key, CompiledModule.Definition> definitions,
    Map<String, Map<BoundLocalId, Integer>> callableLocals) {
  ImportedCompilation {
    contributions = Map.copyOf(contributions);
    mappings = Map.copyOf(mappings);
    definitions = Map.copyOf(definitions);
    callableLocals = Map.copyOf(callableLocals);
  }

  static ImportedCompilation empty() {
    return new ImportedCompilation(Map.of(), Map.of(), Map.of(), Map.of());
  }

  static ImportedCompilation create(
      List<CompiledModule> modules,
      List<ParsedDocument> documents,
      CompilationScope scope,
      DeclarationAnalysis declarations,
      java.util.Set<DocumentId> exportedSources,
      java.util.Set<DocumentId> bindingSources) {
    if (modules.isEmpty()) return empty();
    var currentSources =
        new LinkedHashMap<ModuleSourceCoordinate, dev.w0fv1.norm.source.SourceFile>();
    var declarationDocuments = declarations.sourceDocuments();
    documents.stream()
        .filter(document -> declarationDocuments.contains(document.source().id()))
        .forEach(
            document ->
                currentSources.put(scope.coordinate(document.source().id()), document.source()));
    var contracts = declarations.moduleContracts(scope, scope.modules().modules());
    var contributions = new LinkedHashMap<SourceSpan, SemanticContribution>();
    var mappings = new LinkedHashMap<SourceSpan, TokenSpanMapping>();
    var definitions = new LinkedHashMap<CoreBuildHistory.Key, CompiledModule.Definition>();
    var locals = new LinkedHashMap<String, Map<BoundLocalId, Integer>>();
    var imported = new java.util.HashSet<ModuleCoordinate>();
    for (var module : modules) {
      var content = module.content();
      if (!imported.add(module.coordinate()))
        throw new IllegalArgumentException("duplicate compiled module: " + module.coordinate());
      var readable =
          new java.util.LinkedHashSet<>(
              scope.modules().dependencies().getOrDefault(module.coordinate(), java.util.Set.of()));
      readable.add(module.coordinate());
      if (!readable.equals(content.contracts().keySet()))
        throw new IllegalArgumentException(
            "published Core module dependencies do not match: " + module.coordinate());
      var exports =
          exportedSources.stream()
              .map(scope::coordinate)
              .filter(currentSources::containsKey)
              .filter(coordinate -> coordinate.module().equals(module.coordinate()))
              .collect(java.util.stream.Collectors.toSet());
      var bindings =
          bindingSources.stream()
              .map(scope::coordinate)
              .filter(currentSources::containsKey)
              .filter(coordinate -> coordinate.module().equals(module.coordinate()))
              .collect(java.util.stream.Collectors.toSet());
      if (!exports.equals(content.exportedSources()) || !bindings.equals(content.bindingSources()))
        throw new IllegalArgumentException(
            "published Core source policy does not match: " + module.coordinate());
      for (var required : content.contracts().entrySet()) {
        var current = contracts.get(required.getKey());
        boolean compatible =
            current != null
                && (required.getKey().equals(module.coordinate())
                    ? required.getValue().equals(current)
                    : current.entrySet().containsAll(required.getValue().entrySet()));
        if (!compatible)
          throw new IllegalArgumentException(
              "published Core declaration contract does not match: " + required.getKey());
      }
      var owned =
          currentSources.keySet().stream()
              .filter(coordinate -> coordinate.module().equals(module.coordinate()))
              .collect(java.util.stream.Collectors.toSet());
      if (!owned.equals(content.sources().keySet()))
        throw new IllegalArgumentException(
            "published Core source set does not match: " + module.coordinate());
      var relocated = new LinkedHashMap<DocumentId, TokenSpanMapping>();
      content
          .sources()
          .forEach(
              (coordinate, source) -> {
                var mapping = TokenSpanMapping.relocate(source, currentSources.get(coordinate));
                relocated.put(source.id(), mapping);
              });
      content
          .contributions()
          .forEach(
              (span, contribution) -> {
                var mapping = relocated.get(span.source().id());
                contributions.put(mapping.rebase(span), contribution.rebase(mapping));
                mappings.put(span, mapping);
              });
      content
          .definitions()
          .forEach(
              (key, definition) -> {
                var mapping = relocated.get(definition.origin().rootSpan().source().id());
                var nodes = new LinkedHashMap<Integer, SourceSpan>();
                definition
                    .origin()
                    .nodeSpans()
                    .forEach((node, span) -> nodes.put(node, mapping.rebase(span)));
                var origin =
                    new CoreDefinitionOrigin(
                        definition.origin().definitionName(),
                        mapping.rebase(definition.origin().rootSpan()),
                        nodes);
                if (definitions.putIfAbsent(
                        key,
                        new CompiledModule.Definition(
                            definition.unit(),
                            origin,
                            definition.references(),
                            definition.binding(),
                            definition.role()))
                    != null)
                  throw new IllegalArgumentException("duplicate imported Core declaration: " + key);
              });
      locals.putAll(content.callableLocals());
    }
    return new ImportedCompilation(contributions, mappings, definitions, locals);
  }

  IncrementalAnalysisPlan merge(IncrementalAnalysisPlan analysis) {
    var reused = new LinkedHashMap<>(contributions);
    reused.putAll(analysis.reusable());
    var rebased = new LinkedHashMap<>(mappings);
    rebased.putAll(analysis.mappings());
    return new IncrementalAnalysisPlan(reused, rebased, analysis.declarations(), reused.size());
  }
}
