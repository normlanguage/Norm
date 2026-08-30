package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CompilationPrelude {
  private static final CompilationPrelude EMPTY =
      new CompilationPrelude(Map.of(), Set.of(), Optional.empty());
  private final Map<DocumentId, ParsedDocument> documents;
  private final Set<DocumentId> exportedSources;
  private final Optional<CompilationScope> scope;

  public CompilationPrelude(
      List<SourceFile> sources, Set<DocumentId> exportedSources, CompilationScope scope) {
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
    this.documents = Map.copyOf(parsed);
    this.exportedSources = exported;
    this.scope = Optional.of(scope);
  }

  private CompilationPrelude(
      Map<DocumentId, ParsedDocument> documents,
      Set<DocumentId> exportedSources,
      Optional<CompilationScope> scope) {
    this.documents = Map.copyOf(documents);
    this.exportedSources = Set.copyOf(exportedSources);
    this.scope = scope;
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
    Optional<CompilationScope> mergedScope =
        mergedDocuments.isEmpty()
            ? Optional.empty()
            : scope.map(value -> other.scope.map(value::merge).orElse(value)).or(() -> other.scope);
    return new CompilationPrelude(mergedDocuments, mergedExports, mergedScope);
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
        exportedSources);
  }

  public Optional<SourceFile> source(DocumentId document) {
    return Optional.ofNullable(documents.get(document)).map(ParsedDocument::source);
  }
}
