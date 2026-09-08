package dev.w0fv1.norm.project;

import dev.w0fv1.norm.frontend.SourceHeader;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleSourceLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ModuleSourceFiles(Map<String, SourceFile> sources, Set<DocumentId> tests) {
  public ModuleSourceFiles {
    sources = Map.copyOf(sources);
    tests = Set.copyOf(tests);
  }

  static ModuleSourceFiles load(
      SourceFile module,
      String name,
      ModuleSourceLayout layout,
      Map<Path, SourceFile> overlays,
      boolean includeTests)
      throws IOException {
    Path directory = module.path().toAbsolutePath().normalize().getParent();
    Map<String, SourceFile> sources = new LinkedHashMap<>();
    Set<DocumentId> tests = new LinkedHashSet<>();
    for (var entry : collectSourceFiles(directory, module, overlays).entrySet()) {
      var selected = layout.locate(directory, entry.getKey());
      if (selected.isEmpty()) continue;
      var root = selected.orElseThrow();
      if (root.kind() == ModuleSourceLayout.Kind.TEST && !includeTests) continue;
      String path =
          name.replace('.', '/')
              + "/"
              + root.directory().relativize(entry.getKey()).toString().replace('\\', '/');
      if (sources.putIfAbsent(path, entry.getValue()) != null) {
        throw new IOException("source roots contain duplicate module path: " + path);
      }
      if (root.kind() == ModuleSourceLayout.Kind.TEST) tests.add(entry.getValue().id());
    }
    return new ModuleSourceFiles(sources, tests);
  }

  static Map<Path, SourceFile> collectSourceFiles(
      Path root, SourceFile moduleSource, Map<Path, SourceFile> overlays) throws IOException {
    Path modulePath = normalize(moduleSource.path());
    Map<Path, SourceFile> sources = new LinkedHashMap<>();
    List<Path> diskSources = List.of();
    if (Files.isDirectory(root)) {
      try (var paths = Files.walk(root)) {
        diskSources =
            paths
                .filter(Files::isRegularFile)
                .filter(ModuleSourceFiles::isNormSource)
                .map(ModuleSourceFiles::normalize)
                .sorted(Comparator.comparing(Path::toString))
                .toList();
      }
    }
    Set<Path> nestedRoots = nestedModuleRoots(root, modulePath, diskSources, overlays);
    for (Path path : diskSources) {
      if (!path.equals(modulePath) && !insideNestedModule(path, nestedRoots)) {
        SourceFile overlay = overlays.get(path);
        sources.put(path, overlay == null ? SourceFile.read(path) : overlay);
      }
    }
    overlays.entrySet().stream()
        .filter(source -> source.getKey().startsWith(root))
        .filter(source -> isNormSource(source.getKey()))
        .filter(source -> !source.getKey().equals(modulePath))
        .filter(source -> !insideNestedModule(source.getKey(), nestedRoots))
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(Path::toString)))
        .forEach(source -> sources.put(source.getKey(), source.getValue()));
    return sources;
  }

  private static Set<Path> nestedModuleRoots(
      Path root, Path modulePath, List<Path> diskSources, Map<Path, SourceFile> overlays)
      throws IOException {
    Map<Path, SourceFile> candidates = new LinkedHashMap<>();
    for (Path path : diskSources) {
      if (!path.equals(modulePath) && path.getFileName().toString().equals("module.norm")) {
        SourceFile overlay = overlays.get(path);
        candidates.put(path, overlay == null ? SourceFile.read(path) : overlay);
      }
    }
    overlays.entrySet().stream()
        .filter(source -> source.getKey().startsWith(root))
        .filter(source -> !source.getKey().equals(modulePath))
        .filter(source -> source.getKey().getFileName().toString().equals("module.norm"))
        .forEach(source -> candidates.put(source.getKey(), source.getValue()));
    List<Path> candidateRoots =
        candidates.entrySet().stream()
            .filter(source -> isModuleSource(source.getValue()))
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

  private static boolean isNormSource(Path path) {
    return path.getFileName().toString().endsWith(".norm");
  }

  public static boolean isModuleSource(SourceFile source) {
    Objects.requireNonNull(source, "source");
    Path path = source.path();
    return path.getFileName().toString().equals("module.norm")
        && SourceHeader.parse(source).packageName().isEmpty();
  }

  private static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }
}
