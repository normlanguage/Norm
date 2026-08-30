package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ModuleLoader {
  public ModuleLoader() {}

  public LoadedModule load(ModuleSourceResolver resolver, ModuleDescriptor descriptor)
      throws IOException {
    Set<String> exportedPaths = new LinkedHashSet<>();
    for (String exportedName : descriptor.exports()) {
      String path = descriptor.sourcePath(exportedName);
      exportedPaths.add(path);
    }
    Map<String, SourceFile> sources = new LinkedHashMap<>();
    String modulePrefix = descriptor.name().replace('.', '/') + "/";
    for (String path : resolver.listSources().stream().distinct().sorted().toList()) {
      if (!path.endsWith(".norm")) continue;
      if (!path.startsWith(modulePrefix)) {
        throw new IOException(
            "source '" + path + "' is outside module package '" + descriptor.name() + "'");
      }
      SourceFile source = resolver.read(path);
      requirePackage(source, parent(path).replace('/', '.'));
      sources.put(path, source);
    }
    for (String exportedPath : exportedPaths) {
      if (!sources.containsKey(exportedPath)) {
        throw new IOException("source '" + exportedPath + "' does not exist");
      }
    }
    Set<DocumentId> exportedSources = new LinkedHashSet<>();
    for (String path : exportedPaths) exportedSources.add(sources.get(path).id());
    return new LoadedModule(descriptor, sources, exportedSources);
  }

  private static void requirePackage(SourceFile source, String expectedPackage) throws IOException {
    String packageName = SourceHeader.parse(source).packageName().orElse("");
    if (!packageName.equals(expectedPackage)) {
      throw new IOException(
          "source '" + source.displayName() + "' must declare package '" + expectedPackage + "'");
    }
  }

  private static String parent(String path) {
    int separator = path.lastIndexOf('/');
    return separator < 0 ? "" : path.substring(0, separator);
  }

  public record LoadedModule(
      ModuleDescriptor descriptor,
      Map<String, SourceFile> sources,
      Set<DocumentId> exportedSources) {
    public LoadedModule {
      sources = Map.copyOf(sources);
      exportedSources = Set.copyOf(exportedSources);
    }
  }
}
