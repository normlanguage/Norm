package dev.w0fv1.norm.stdlib;

import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.frontend.ModuleSourceResolver;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class StandardLibrary {
  private static final LoadedModule MODULE = load();

  private StandardLibrary() {}

  public static List<SourceFile> sources() {
    return MODULE.sources();
  }

  public static Optional<SourceFile> source(DocumentId document) {
    return MODULE.sources().stream().filter(source -> source.id().equals(document)).findFirst();
  }

  public static Set<DocumentId> exportedSources() {
    return MODULE.exportedSources();
  }

  private static LoadedModule load() {
    Module module = StandardLibrary.class.getModule();
    try (ResourceResolver resolver = new ResourceResolver(module)) {
      ModuleLoader.LoadedModule loaded = new ModuleLoader().load(resolver);
      return new LoadedModule(loaded.sources(), loaded.exportedSources());
    } catch (IOException exception) {
      throw new IllegalStateException("cannot load standard library", exception);
    }
  }

  private static final class ResourceResolver implements ModuleSourceResolver {
    private final Module module;
    private final ModuleReader reader;

    private ResourceResolver(Module module) throws IOException {
      this.module = module;
      reader = openReader(module);
    }

    @Override
    public SourceFile read(String relativePath) throws IOException {
      try (InputStream stream = requireResource(module, relativePath)) {
        return SourceFile.of(
            DocumentId.of("stdlib:/" + relativePath),
            new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
    }

    @Override
    public List<String> list(String relativeDirectory) throws IOException {
      String prefix = relativeDirectory.isEmpty() ? "" : relativeDirectory + "/";
      if (reader != null) {
        try (var resources = reader.list()) {
          return resources
              .filter(path -> path.startsWith(prefix))
              .filter(path -> path.indexOf('/', prefix.length()) < 0)
              .toList();
        }
      }
      List<String> resources = new ArrayList<>();
      Enumeration<URL> directories = classLoader(module).getResources(relativeDirectory);
      while (directories.hasMoreElements()) {
        URL directory = directories.nextElement();
        if (directory.getProtocol().equals("file")) {
          try (var paths = Files.list(Path.of(URI.create(directory.toString())))) {
            paths
                .filter(Files::isRegularFile)
                .map(path -> prefix + path.getFileName())
                .forEach(resources::add);
          }
        } else if (directory.getProtocol().equals("jar")) {
          try (var jar = ((JarURLConnection) directory.openConnection()).getJarFile()) {
            jar.stream()
                .map(java.util.jar.JarEntry::getName)
                .filter(path -> path.startsWith(prefix))
                .filter(path -> path.indexOf('/', prefix.length()) < 0)
                .forEach(resources::add);
          }
        }
      }
      return List.copyOf(resources);
    }

    @Override
    public void close() throws IOException {
      if (reader != null) reader.close();
    }

    private static ModuleReader openReader(Module module) throws IOException {
      if (!module.isNamed() || module.getLayer() == null) return null;
      return module
          .getLayer()
          .configuration()
          .findModule(module.getName())
          .orElseThrow(() -> new IOException("cannot resolve module " + module.getName()))
          .reference()
          .open();
    }

    private static ClassLoader classLoader(Module module) {
      ClassLoader loader = module.getClassLoader();
      return loader == null ? ClassLoader.getSystemClassLoader() : loader;
    }
  }

  private static InputStream requireResource(Module module, String path) throws IOException {
    InputStream stream = module.getResourceAsStream(path);
    if (stream == null)
      throw new IllegalStateException("missing standard-library resource " + path);
    return stream;
  }

  private record LoadedModule(List<SourceFile> sources, Set<DocumentId> exportedSources) {}
}
