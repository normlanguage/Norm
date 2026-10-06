package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CompilationPrelude {
  private static final CompilationPrelude EMPTY =
      new CompilationPrelude(Map.of(), Set.of(), Optional.empty(), Set.of(), Map.of());
  private final Map<DocumentId, ParsedDocument> documents;
  private final Set<DocumentId> exportedSources;
  private final Optional<CompilationScope> scope;
  private final Set<DocumentId> bindingSources;
  private final Map<DocumentId, List<dev.w0fv1.norm.semantic.BuiltinTypeConformance>>
      builtinTypeConformances;

  public CompilationPrelude(
      List<SourceFile> sources, Set<DocumentId> exportedSources, CompilationScope scope) {
    this(sources, exportedSources, scope, Set.of());
  }

  public CompilationPrelude(
      List<SourceFile> sources,
      Set<DocumentId> exportedSources,
      CompilationScope scope,
      Set<DocumentId> bindingSources) {
    this(sources, exportedSources, scope, bindingSources, Map.of());
  }

  public CompilationPrelude(
      List<SourceFile> sources,
      Set<DocumentId> exportedSources,
      CompilationScope scope,
      Set<DocumentId> bindingSources,
      Map<DocumentId, List<dev.w0fv1.norm.semantic.BuiltinTypeConformance>>
          builtinTypeConformances) {
    Objects.requireNonNull(sources, "sources");
    Map<DocumentId, ParsedDocument> parsed = new LinkedHashMap<>();
    for (SourceFile source : sources) {
      if (parsed.putIfAbsent(source.id(), SourceParser.parse(source)) != null) {
        throw new IllegalArgumentException("duplicate prelude source " + source.id().uri());
      }
    }
    Set<DocumentId> exported = Set.copyOf(exportedSources);
    if (!parsed.keySet().containsAll(exported)) {
      throw new IllegalArgumentException("exported prelude documents must be prelude sources");
    }
    if (!scope.coordinates().keySet().equals(parsed.keySet())) {
      throw new IllegalArgumentException("prelude scope must describe every source");
    }
    this.bindingSources = Set.copyOf(bindingSources);
    if (!parsed.keySet().containsAll(this.bindingSources)) {
      throw new IllegalArgumentException("binding prelude documents must be prelude sources");
    }
    if (!this.bindingSources.containsAll(builtinTypeConformances.keySet())) {
      throw new IllegalArgumentException("builtin conformances require generated binding sources");
    }
    var conformances =
        new LinkedHashMap<DocumentId, List<dev.w0fv1.norm.semantic.BuiltinTypeConformance>>();
    builtinTypeConformances.forEach((id, values) -> conformances.put(id, List.copyOf(values)));
    this.builtinTypeConformances = Map.copyOf(conformances);
    this.documents = Map.copyOf(parsed);
    this.exportedSources = exported;
    this.scope = Optional.of(scope);
  }

  private CompilationPrelude(
      Map<DocumentId, ParsedDocument> documents,
      Set<DocumentId> exportedSources,
      Optional<CompilationScope> scope,
      Set<DocumentId> bindingSources,
      Map<DocumentId, List<dev.w0fv1.norm.semantic.BuiltinTypeConformance>>
          builtinTypeConformances) {
    this.documents = Map.copyOf(documents);
    this.exportedSources = Set.copyOf(exportedSources);
    this.scope = scope;
    this.bindingSources = Set.copyOf(bindingSources);
    this.builtinTypeConformances = Map.copyOf(builtinTypeConformances);
  }

  public static CompilationPrelude empty() {
    return EMPTY;
  }

  public CompilationPrelude merge(CompilationPrelude other) {
    Objects.requireNonNull(other, "other");
    Map<DocumentId, ParsedDocument> mergedDocuments = new LinkedHashMap<>(documents);
    for (Map.Entry<DocumentId, ParsedDocument> document : other.documents.entrySet()) {
      if (mergedDocuments.putIfAbsent(document.getKey(), document.getValue()) != null) {
        throw new IllegalArgumentException("duplicate prelude source " + document.getKey().uri());
      }
    }
    Set<DocumentId> mergedExports = new LinkedHashSet<>(exportedSources);
    mergedExports.addAll(other.exportedSources);
    Set<DocumentId> mergedBindings = new LinkedHashSet<>(bindingSources);
    mergedBindings.addAll(other.bindingSources);
    Optional<CompilationScope> mergedScope =
        mergedDocuments.isEmpty()
            ? Optional.empty()
            : scope.map(value -> other.scope.map(value::merge).orElse(value)).or(() -> other.scope);
    var conformances = new LinkedHashMap<>(builtinTypeConformances);
    conformances.putAll(other.builtinTypeConformances);
    return new CompilationPrelude(
        mergedDocuments, mergedExports, mergedScope, mergedBindings, conformances);
  }

  CompilationPrelude excludingModules(
      java.util.Set<dev.w0fv1.norm.value.ModuleCoordinate> modules) {
    if (scope.isEmpty()) return this;
    var current = scope.orElseThrow();
    var retained = new LinkedHashMap<DocumentId, ParsedDocument>();
    var coordinates = new LinkedHashMap<DocumentId, dev.w0fv1.norm.value.ModuleSourceCoordinate>();
    documents.forEach(
        (id, document) -> {
          if (!modules.contains(current.coordinate(id).module())) {
            retained.put(id, document);
            coordinates.put(id, current.coordinate(id));
          }
        });
    if (retained.size() == documents.size()) return this;
    if (retained.isEmpty()) return empty();
    var exports = new LinkedHashSet<>(exportedSources);
    exports.retainAll(retained.keySet());
    var bindings = new LinkedHashSet<>(bindingSources);
    bindings.retainAll(retained.keySet());
    var conformances = new LinkedHashMap<>(builtinTypeConformances);
    conformances.keySet().retainAll(retained.keySet());
    var dependencies =
        new LinkedHashMap<
            dev.w0fv1.norm.value.ModuleCoordinate, Set<dev.w0fv1.norm.value.ModuleCoordinate>>();
    current
        .modules()
        .dependencies()
        .forEach(
            (module, targets) -> {
              if (!modules.contains(module)) {
                var reads = new LinkedHashSet<>(targets);
                reads.removeAll(modules);
                dependencies.put(module, Set.copyOf(reads));
              }
            });
    return new CompilationPrelude(
        retained,
        exports,
        Optional.of(
            new CompilationScope(coordinates, new dev.w0fv1.norm.value.ModuleGraph(dependencies))),
        bindings,
        conformances);
  }

  List<ParsedDocument> documents() {
    return List.copyOf(documents.values());
  }

  Set<DocumentId> exportedSources() {
    return exportedSources;
  }

  Set<DocumentId> documentIds() {
    return documents.keySet();
  }

  Set<DocumentId> bindingSources() {
    return bindingSources;
  }

  Map<DocumentId, List<dev.w0fv1.norm.semantic.BuiltinTypeConformance>> builtinTypeConformances() {
    return builtinTypeConformances;
  }

  Optional<CompilationScope> scope() {
    return scope;
  }

  CompilationRequest request(DocumentId entryDocument) {
    if (!documents.containsKey(Objects.requireNonNull(entryDocument, "entryDocument"))) {
      throw new IllegalArgumentException("source is not part of the compilation prelude");
    }
    return new CompilationRequest(
        new CompilationUnitId(entryDocument.uri()),
        scope.orElseThrow(),
        entryDocument,
        documents.values().stream().map(ParsedDocument::source).toList(),
        exportedSources,
        bindingSources);
  }

  public Optional<SourceFile> source(DocumentId document) {
    return Optional.ofNullable(documents.get(document)).map(ParsedDocument::source);
  }
}
