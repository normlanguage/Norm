package dev.w0fv1.norm.project;

import static dev.w0fv1.norm.project.ProjectPaths.relativePath;

import dev.w0fv1.norm.frontend.ModuleSourceResolver;
import dev.w0fv1.norm.source.SourceFile;
import java.io.IOException;
import java.util.List;
import java.util.Map;

record ModuleSourceSnapshot(Map<String, SourceFile> sources) implements ModuleSourceResolver {
  ModuleSourceSnapshot {
    sources = Map.copyOf(sources);
  }

  @Override
  public SourceFile read(String relativePath) throws IOException {
    SourceFile source = sources.get(relativePath);
    if (source == null) throw new IOException("source '" + relativePath + "' does not exist");
    return source;
  }

  @Override
  public List<String> listSources() {
    return List.copyOf(sources.keySet());
  }
}
