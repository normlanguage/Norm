package dev.w0fv1.norm.stdlib;

import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.frontend.ModuleSourceResolver;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

public final class StandardLibrary {
  private StandardLibrary() {}

  public static SourceFile moduleSource() {
    try (InputStream stream = requireResource("std/module.norm")) {
      return SourceFile.of(
          DocumentId.of("stdlib:/std/module.norm"),
          new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new IllegalStateException("cannot load standard-library module source", exception);
    }
  }

  public static LoadedModule load(ModuleDescriptor descriptor) {
    try (ResourceResolver resolver = new ResourceResolver(descriptor)) {
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
              sourcePaths));
    } catch (IOException exception) {
      throw new IllegalStateException("cannot load standard library", exception);
    }
  }

  private static final class ResourceResolver implements ModuleSourceResolver {
    private final List<String> sources;

    private ResourceResolver(ModuleDescriptor descriptor) {
      sources = descriptor.exports().stream().map(descriptor::sourcePath).toList();
    }

    @Override
    public SourceFile read(String relativePath) throws IOException {
      try (InputStream stream = requireResource(relativePath)) {
        return SourceFile.of(
            DocumentId.of("stdlib:/" + relativePath),
            new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
    }

    @Override
    public List<String> listSources() {
      return sources;
    }
  }

  private static InputStream requireResource(String path) throws IOException {
    InputStream stream = StandardLibrary.class.getResourceAsStream("/" + path);
    if (stream == null)
      throw new IllegalStateException("missing standard-library resource " + path);
    return stream;
  }

  public record LoadedModule(
      List<SourceFile> sources, Set<DocumentId> exportedSources, CompilationScope scope) {
    public LoadedModule {
      sources = List.copyOf(sources);
      exportedSources = Set.copyOf(exportedSources);
    }
  }
}
