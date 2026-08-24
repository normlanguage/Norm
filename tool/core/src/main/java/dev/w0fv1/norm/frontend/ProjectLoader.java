package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ProjectLoader {
  public ProjectLoader() {}

  public ProjectSourceSet load(Path entryPath) throws IOException {
    return load(SourceFile.read(normalize(entryPath)), List.of());
  }

  public ProjectSourceSet load(SourceFile entrySource, Collection<SourceFile> overlays)
      throws IOException {
    Objects.requireNonNull(entrySource, "entrySource");
    Map<Path, SourceFile> overlaySources = overlaySources(entrySource, overlays);
    Path entry = normalize(entrySource.path());
    ProjectLocation location = locate(entry, overlaySources);
    if (location.manifest().isEmpty()) {
      return new ProjectSourceSet(
          location.root(),
          entry,
          Optional.empty(),
          CompilationScope.anonymous(List.of(entrySource)),
          List.of(entrySource),
          Set.of());
    }

    Path root = location.root();
    SourceFile manifestSource = location.manifest().orElseThrow();
    Path manifestPath = normalize(manifestSource.path());
    Map<String, SourceFile> sourcesByRelativePath = new LinkedHashMap<>();
    sourcesByRelativePath.put("module.norm", manifestSource);
    List<Path> diskSources = List.of();
    if (Files.isDirectory(root)) {
      try (var paths = Files.walk(root)) {
        diskSources =
            paths
                .filter(Files::isRegularFile)
                .filter(ProjectLoader::isNormSource)
                .map(ProjectLoader::normalize)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
      }
    }
    Set<Path> nestedModuleRoots =
        nestedModuleRoots(root, manifestPath, diskSources, overlaySources);
    for (Path path : diskSources) {
      if (!path.equals(manifestPath) && !insideNestedModule(path, nestedModuleRoots)) {
        SourceFile source = overlaySources.get(path);
        sourcesByRelativePath.put(
            relativePath(root, path), source == null ? SourceFile.read(path) : source);
      }
    }
    overlaySources.entrySet().stream()
        .filter(source -> source.getKey().startsWith(root))
        .filter(source -> isNormSource(source.getKey()))
        .filter(source -> !source.getKey().equals(manifestPath))
        .filter(source -> !insideNestedModule(source.getKey(), nestedModuleRoots))
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(Path::toString)))
        .forEach(
            source ->
                sourcesByRelativePath.put(relativePath(root, source.getKey()), source.getValue()));
    if (!sourcesByRelativePath.containsKey(relativePath(root, entry))) {
      throw new IOException("entry source is not part of the module");
    }

    ModuleLoader moduleLoader = new ModuleLoader();
    MemoryResolver resolver = new MemoryResolver(sourcesByRelativePath);
    ModuleManifest manifest = moduleLoader.load(resolver).manifest();
    Set<String> packages = new LinkedHashSet<>();
    for (String relativeSource : sourcesByRelativePath.keySet()) {
      if (!relativeSource.equals("module.norm")) packages.add(packageName(relativeSource));
    }
    for (String packageName : packages.stream().sorted().toList()) {
      if (!packageName.equals(manifest.name()) && !packageName.startsWith(manifest.name() + ".")) {
        throw new IOException(
            "source package '"
                + packageName
                + "' is outside module package '"
                + manifest.name()
                + "'");
      }
      moduleLoader.loadPackage(resolver, packageName);
    }

    Set<Path> exportedSources = new LinkedHashSet<>();
    for (String exportedName : manifest.exports()) {
      exportedSources.add(normalize(root.resolve(manifest.sourcePath(exportedName))));
    }
    List<SourceFile> sources =
        sourcesByRelativePath.entrySet().stream()
            .filter(source -> !source.getKey().equals("module.norm"))
            .map(Map.Entry::getValue)
            .toList();
    Map<DocumentId, String> sourcePaths = new LinkedHashMap<>();
    for (SourceFile source : sources) {
      sourcePaths.put(source.id(), relativePath(root, normalize(source.path())));
    }
    CompilationScope scope =
        new CompilationScope(
            new ModuleCoordinate(manifest.name(), manifest.version()), sourcePaths);
    return new ProjectSourceSet(
        root, entry, Optional.of(manifestPath), scope, sources, exportedSources);
  }

  public static Path projectRoot(SourceFile source, Collection<SourceFile> overlays) {
    Objects.requireNonNull(source, "source");
    Path path = normalize(source.path());
    try {
      return locate(path, overlaySources(source, overlays)).root();
    } catch (IOException exception) {
      Path fallback = path.getParent();
      if (fallback == null) throw new IllegalArgumentException("source path has no parent");
      return fallback;
    }
  }

  public static boolean isManifest(SourceFile source) {
    Objects.requireNonNull(source, "source");
    return ModuleManifest.isManifest(source) && !declaresPackage(source);
  }

  private static ProjectLocation locate(Path entry, Map<Path, SourceFile> overlays)
      throws IOException {
    Path fallback = entry.getParent();
    if (fallback == null) throw new IllegalArgumentException("source path has no parent");
    Path current = fallback;
    while (current != null) {
      Path candidate = normalize(current.resolve("module.norm"));
      SourceFile overlay = overlays.get(candidate);
      if (overlay != null) {
        if (isManifest(overlay)) return new ProjectLocation(current, Optional.of(overlay));
      } else if (Files.isRegularFile(candidate)) {
        SourceFile source = SourceFile.read(candidate);
        if (isManifest(source)) return new ProjectLocation(current, Optional.of(source));
      }
      current = current.getParent();
    }
    return new ProjectLocation(fallback, Optional.empty());
  }

  private static Map<Path, SourceFile> overlaySources(
      SourceFile entrySource, Collection<SourceFile> overlays) {
    Objects.requireNonNull(overlays, "overlays");
    Map<Path, SourceFile> sources = new LinkedHashMap<>();
    for (SourceFile overlay : overlays) {
      sources.put(normalize(overlay.path()), overlay);
    }
    sources.put(normalize(entrySource.path()), entrySource);
    return sources;
  }

  private static Set<Path> nestedModuleRoots(
      Path root, Path manifestPath, List<Path> diskSources, Map<Path, SourceFile> overlays)
      throws IOException {
    Map<Path, SourceFile> candidates = new LinkedHashMap<>();
    for (Path path : diskSources) {
      if (!path.equals(manifestPath) && path.getFileName().toString().equals("module.norm")) {
        SourceFile overlay = overlays.get(path);
        candidates.put(path, overlay == null ? SourceFile.read(path) : overlay);
      }
    }
    overlays.entrySet().stream()
        .filter(source -> source.getKey().startsWith(root))
        .filter(source -> !source.getKey().equals(manifestPath))
        .filter(source -> source.getKey().getFileName().toString().equals("module.norm"))
        .forEach(source -> candidates.put(source.getKey(), source.getValue()));
    List<Path> candidateRoots =
        candidates.entrySet().stream()
            .filter(source -> isManifest(source.getValue()))
            .map(source -> source.getKey().getParent())
            .sorted(
                Comparator.comparingInt(Path::getNameCount)
                    .thenComparing(Comparator.comparing(Path::toString)))
            .toList();
    Set<Path> nestedRoots = new LinkedHashSet<>();
    for (Path candidate : candidateRoots) {
      if (!insideNestedModule(candidate, nestedRoots)) nestedRoots.add(candidate);
    }
    return Set.copyOf(nestedRoots);
  }

  private static boolean insideNestedModule(Path path, Set<Path> nestedRoots) {
    return nestedRoots.stream().anyMatch(path::startsWith);
  }

  private static boolean declaresPackage(SourceFile source) {
    DiagnosticBag diagnostics = new DiagnosticBag();
    return new Parser(source, new Lexer(source, diagnostics).lex(), diagnostics)
        .parsePackageDeclaration()
        .isPresent();
  }

  private static String packageName(String relativeSource) {
    int separator = relativeSource.lastIndexOf('/');
    return separator < 0 ? "" : relativeSource.substring(0, separator).replace('/', '.');
  }

  private static String relativePath(Path root, Path path) {
    return root.relativize(path).toString().replace('\\', '/');
  }

  private static boolean isNormSource(Path path) {
    return path.getFileName().toString().endsWith(".norm");
  }

  private static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }

  private record ProjectLocation(Path root, Optional<SourceFile> manifest) {}

  private static final class MemoryResolver implements ModuleSourceResolver {
    private final Map<String, SourceFile> sources;

    private MemoryResolver(Map<String, SourceFile> sources) {
      this.sources = Map.copyOf(sources);
    }

    @Override
    public SourceFile read(String relativePath) throws IOException {
      SourceFile source = sources.get(relativePath);
      if (source == null) {
        throw new IOException("source '" + relativePath + "' does not exist");
      }
      return source;
    }

    @Override
    public List<String> list(String relativeDirectory) {
      String prefix = relativeDirectory.isEmpty() ? "" : relativeDirectory + "/";
      return sources.keySet().stream()
          .filter(path -> !path.equals("module.norm"))
          .filter(path -> path.startsWith(prefix))
          .filter(path -> path.indexOf('/', prefix.length()) < 0)
          .toList();
    }
  }
}
