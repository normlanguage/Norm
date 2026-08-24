package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModuleLoader {
  public ModuleLoader() {}

  public LoadedModule load(ModuleSourceResolver resolver) throws IOException {
    ModuleManifest manifest;
    try {
      manifest = new ModuleManifestParser().parse(resolver.read("module.norm"));
    } catch (IllegalArgumentException exception) {
      throw new IOException(exception.getMessage(), exception);
    }
    Set<String> exportedPaths = new LinkedHashSet<>();
    Set<String> packageDirectories = new LinkedHashSet<>();
    for (String exportedName : manifest.exports()) {
      String path = manifest.sourcePath(exportedName);
      exportedPaths.add(path);
      packageDirectories.add(parent(path));
    }
    Map<String, SourceFile> sources = new LinkedHashMap<>();
    for (String path : exportedPaths) {
      SourceFile source = resolver.read(path);
      requirePackage(source, parent(path).replace('/', '.'));
      sources.put(path, source);
    }
    for (String directory : packageDirectories) {
      for (String path : resolver.list(directory).stream().sorted().toList()) {
        if (!path.endsWith(".norm")) continue;
        SourceFile source = resolver.read(path);
        requirePackage(source, directory.replace('/', '.'));
        sources.put(path, source);
      }
    }
    Set<DocumentId> exportedSources = new LinkedHashSet<>();
    for (String path : exportedPaths) exportedSources.add(sources.get(path).id());
    return new LoadedModule(
        manifest,
        sources.values().stream()
            .sorted(Comparator.comparing(source -> source.id().uri().toString()))
            .toList(),
        exportedSources);
  }

  public List<SourceFile> loadPackage(ModuleSourceResolver resolver, String packageName)
      throws IOException {
    String directory = packageName.replace('.', '/');
    List<SourceFile> sources = new ArrayList<>();
    for (String path : resolver.list(directory).stream().sorted().toList()) {
      if (!path.endsWith(".norm")) continue;
      SourceFile source = resolver.read(path);
      requirePackage(source, packageName);
      sources.add(source);
    }
    return List.copyOf(sources);
  }

  private static void requirePackage(SourceFile source, String expectedPackage) throws IOException {
    DiagnosticBag diagnostics = new DiagnosticBag();
    String packageName =
        new Parser(source, new Lexer(source, diagnostics).lex(), diagnostics)
            .parsePackageDeclaration()
            .orElse("");
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
      ModuleManifest manifest, List<SourceFile> sources, Set<DocumentId> exportedSources) {
    public LoadedModule {
      sources = List.copyOf(sources);
      exportedSources = Set.copyOf(exportedSources);
    }
  }
}
