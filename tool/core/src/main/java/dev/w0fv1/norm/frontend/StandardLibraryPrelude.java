package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.stdlib.StandardLibrary;
import dev.w0fv1.norm.value.DocumentId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StandardLibraryPrelude {
  private final Map<DocumentId, ParsedDocument> documents;
  private final Set<DocumentId> exportedSources;

  StandardLibraryPrelude(CompilationEnvironment environment) {
    Map<DocumentId, ParsedDocument> parsed = new LinkedHashMap<>();
    StandardLibrary.sources()
        .forEach(source -> parsed.put(source.id(), environment.parse(source, false)));
    documents = Map.copyOf(parsed);
    exportedSources = Set.copyOf(StandardLibrary.exportedSources());
  }

  List<ParsedDocument> documents() {
    return List.copyOf(documents.values());
  }

  Set<DocumentId> exportedSources() {
    return exportedSources;
  }
}
