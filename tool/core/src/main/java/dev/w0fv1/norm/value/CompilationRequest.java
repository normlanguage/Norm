package dev.w0fv1.norm.value;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record CompilationRequest(
    DocumentId entryDocument, List<SourceFile> sources, Set<DocumentId> exportedSources) {
  public CompilationRequest {
    Objects.requireNonNull(entryDocument, "entryDocument");
    sources = List.copyOf(sources);
    if (sources.isEmpty()) throw new IllegalArgumentException("compilation requires source files");
    Map<DocumentId, SourceFile> unique = new LinkedHashMap<>();
    for (SourceFile source : sources) {
      if (unique.putIfAbsent(source.id(), source) != null) {
        throw new IllegalArgumentException("duplicate source document " + source.id().uri());
      }
    }
    if (!unique.containsKey(entryDocument)) {
      throw new IllegalArgumentException("entry document is not part of the compilation");
    }
    exportedSources = Set.copyOf(exportedSources);
    if (!unique.keySet().containsAll(exportedSources)) {
      throw new IllegalArgumentException("exported documents must be part of the compilation");
    }
  }

  public CompilationRequest(DocumentId entryDocument, List<SourceFile> sources) {
    this(entryDocument, sources, Set.of());
  }

  public static CompilationRequest single(SourceFile source) {
    Objects.requireNonNull(source, "source");
    return new CompilationRequest(source.id(), List.of(source));
  }

  public SourceFile entrySource() {
    return sources.stream()
        .filter(source -> source.id().equals(entryDocument))
        .findFirst()
        .orElseThrow();
  }
}
