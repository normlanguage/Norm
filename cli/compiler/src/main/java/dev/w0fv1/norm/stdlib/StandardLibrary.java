package dev.w0fv1.norm.stdlib;

import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.frontend.ModuleSourceResolver;
import dev.w0fv1.norm.jvm.GeneratedBindingSource;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StandardLibrary {
  private StandardLibrary() {}

  public static SourceFile moduleSource() {
    return moduleSource("std");
  }

  public static SourceFile moduleSource(String module) {
    String path = module.replace('.', '/') + "/module.norm";
    try (InputStream stream = requireResource(path)) {
      return SourceFile.of(
          DocumentId.of("stdlib:/" + path),
          new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new IllegalStateException("cannot load standard-library module source", exception);
    }
  }

  public static LoadedModule load(ModuleDescriptor descriptor) {
    return load(descriptor, List.of());
  }

  public static LoadedModule load(
      ModuleDescriptor descriptor, List<GeneratedBindingSource> bindings) {
    try (ResourceResolver resolver = new ResourceResolver(descriptor, bindings)) {
      ModuleLoader.LoadedModule loaded = new ModuleLoader().load(resolver, descriptor);
      java.util.Map<DocumentId, String> sourcePaths = new java.util.LinkedHashMap<>();
      loaded
          .sources()
          .values()
          .forEach(source -> sourcePaths.put(source.id(), source.id().uri().getPath()));
      return new LoadedModule(
          List.copyOf(loaded.sources().values()),
          loaded.exportedSources(),
          CompilationScope.module(
              new ModuleCoordinate(loaded.descriptor().name(), loaded.descriptor().version()),
              sourcePaths),
          resolver.bindingSources.keySet().stream()
              .map(path -> DocumentId.of("stdlib:/" + path))
              .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    } catch (IOException exception) {
      throw new IllegalStateException("cannot load standard library", exception);
    }
  }

  private static final class ResourceResolver implements ModuleSourceResolver {
    private final List<String> sources;
    private final Map<String, SourceFile> bindingSources;

    private ResourceResolver(ModuleDescriptor descriptor, List<GeneratedBindingSource> bindings) {
      sources = descriptor.exports().stream().map(descriptor::sourcePath).toList();
      var generated = new LinkedHashMap<String, SourceFile>();
      for (GeneratedBindingSource binding : bindings) {
        String path = binding.relativePath();
        if (generated.putIfAbsent(
                path, SourceFile.of(DocumentId.of("stdlib:/" + path), binding.text()))
            != null) {
          throw new IllegalArgumentException("duplicate generated standard-library source " + path);
        }
      }
      bindingSources = Map.copyOf(generated);
    }

    @Override
    public SourceFile read(String relativePath) throws IOException {
      SourceFile generated = bindingSources.get(relativePath);
      if (generated != null) return generated;
      try (InputStream stream = requireResource(relativePath)) {
        return SourceFile.of(
            DocumentId.of("stdlib:/" + relativePath),
            new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
    }

    @Override
    public List<String> listSources() {
      return java.util.stream.Stream.concat(sources.stream(), bindingSources.keySet().stream())
          .distinct()
          .sorted()
          .toList();
    }
  }

  private static InputStream requireResource(String path) throws IOException {
    InputStream stream = StandardLibrary.class.getResourceAsStream("/stdlib/" + path);
    if (stream == null)
      throw new IllegalStateException("missing standard-library resource " + path);
    return stream;
  }

  public record LoadedModule(
      List<SourceFile> sources,
      Set<DocumentId> exportedSources,
      CompilationScope scope,
      Set<DocumentId> bindingSources) {
    public LoadedModule {
      sources = List.copyOf(sources);
      exportedSources = Set.copyOf(exportedSources);
      bindingSources = Set.copyOf(bindingSources);
    }
  }
}
