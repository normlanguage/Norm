package dev.w0fv1.norm.project;

import static dev.w0fv1.norm.project.ProjectPaths.normalize;
import static dev.w0fv1.norm.project.ProjectPaths.repositoryRoot;

import dev.w0fv1.norm.frontend.ModuleLoader;
import dev.w0fv1.norm.jvm.GeneratedBindingSource;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.packages.NormPackageResolver;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.ModuleDescriptor;
import dev.w0fv1.norm.value.ModuleRequirement;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

final class ArchivedModuleLoader {
  private final NormPackageResolver packages;
  private final JarResolver jars;
  private final java.util.function.Consumer<String> progress;
  private final Map<Path, ModuleArchiveReader.ArchivedModule> archives =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<AnalysisModuleKey, ResolvedProjectModule> analysisModules =
      new java.util.concurrent.ConcurrentHashMap<>();

  ArchivedModuleLoader(
      NormPackageResolver packages,
      JarResolver jars,
      java.util.function.Consumer<String> progress) {
    this.packages = Objects.requireNonNull(packages, "packages");
    this.jars = Objects.requireNonNull(jars, "jars");
    this.progress = Objects.requireNonNull(progress, "progress");
  }

  void clear() {
    archives.clear();
    analysisModules.clear();
  }

  ResolvedProjectModule load(
      Path repositoryRoot,
      ModuleRequirement requirement,
      ProjectLoadPurpose purpose,
      ProjectLoadContext context)
      throws IOException {
    context.checkpoint();
    NormPackageResolver.ResolvedPackage resolution;
    try {
      resolution = packages.resolve(requirement);
    } catch (dev.w0fv1.norm.packages.PackageResolutionException exception) {
      context.inputs().resolution(exception.inputs());
      throw exception;
    }
    context.checkpoint();
    context.inputs().resolution(resolution.inputs());
    Path archive = resolution.archive();
    var archiveSnapshot =
        resolution.inputs().files().stream()
            .filter(snapshot -> snapshot.path().equals(archive))
            .findFirst()
            .orElseThrow();
    AnalysisModuleKey analysisKey = new AnalysisModuleKey(normalize(repositoryRoot), requirement);
    if (purpose == ProjectLoadPurpose.ANALYSIS) {
      ResolvedProjectModule cached = analysisModules.get(analysisKey);
      if (cached != null && cached.archive().orElseThrow().equals(archiveSnapshot)) {
        return cached;
      }
    }
    progress.accept(
        "Resolving NAR: "
            + requirement.repository().value()
            + ":"
            + requirement.name()
            + "@"
            + requirement.version());
    progress.accept("Using NAR: " + archive);
    ModuleArchiveReader.ArchivedModule archived = archive(archiveSnapshot);
    ModuleDescriptor descriptor = archived.descriptor();
    if (!descriptor.coordinate().equals(requirement.coordinate())) {
      throw new IOException(
          "Norm module artifact identity does not match "
              + requirement.name()
              + "@"
              + requirement.version());
    }
    Optional<ResolvedJarBinding> binding = Optional.empty();
    Map<String, String> generatedSources = Map.of();
    if (descriptor.binding().isPresent()) {
      if (purpose != ProjectLoadPurpose.ANALYSIS) {
        progress.accept("Resolving Java dependencies for " + requirement.name());
        ResolvedJarGraph graph =
            jars.resolvePublished(archive, repositoryRoot, descriptor.binding().orElseThrow());
        context.inputs().graph(graph);
        progress.accept("Linking published Java binding for " + requirement.name());
        ResolvedJarBinding resolved = archived.binding().orElseThrow().link(graph);
        Map<String, String> expected = new LinkedHashMap<>();
        for (GeneratedBindingSource source : resolved.generated().sources()) {
          expected.put(source.relativePath(), source.text());
        }
        generatedSources = Map.copyOf(expected);
        binding = Optional.of(resolved);
      } else {
        Map<String, String> expected = new LinkedHashMap<>();
        for (GeneratedBindingSource generated :
            archived.binding().orElseThrow().generated().sources()) {
          String source = archived.sources().get(generated.relativePath());
          if (!generated.text().equals(source))
            throw new IOException(
                "module binding source does not match its published binding: "
                    + generated.relativePath());
          expected.put(generated.relativePath(), source);
        }
        generatedSources = Map.copyOf(expected);
      }
    }
    Path virtualRoot =
        normalize(
            repositoryRoot
                .resolve(".norm/modules")
                .resolve(requirement.repository().value())
                .resolve(requirement.name().replace('.', java.io.File.separatorChar))
                .resolve(Integer.toString(requirement.version())));
    Map<String, SourceFile> sources = new LinkedHashMap<>();
    Set<DocumentId> bindingSources = new LinkedHashSet<>();
    for (Map.Entry<String, String> source : archived.sources().entrySet()) {
      SourceFile generated = SourceFile.of(virtualRoot.resolve(source.getKey()), source.getValue());
      sources.put(source.getKey(), generated);
      if (generatedSources.containsKey(source.getKey())) bindingSources.add(generated.id());
    }
    ModuleLoader.LoadedModule loaded =
        new ModuleLoader().load(new ModuleSourceSnapshot(sources), descriptor);
    ResolvedProjectModule result =
        new ResolvedProjectModule(
            normalize(repositoryRoot.resolve("dependencies")),
            SourceFile.of(archive, ""),
            descriptor,
            loaded.sources(),
            archived.publicSources().stream()
                .map(path -> loaded.sources().get(path).id())
                .collect(java.util.stream.Collectors.toUnmodifiableSet()),
            bindingSources,
            binding,
            archived.resources(),
            Optional.of(archived.archive()),
            Set.of(),
            archived.publicTypes(),
            Optional.of(archived.compiled()));
    if (purpose != ProjectLoadPurpose.ANALYSIS) return result;
    analysisModules.put(analysisKey, result);
    return result;
  }

  private ModuleArchiveReader.ArchivedModule archive(dev.w0fv1.norm.value.FileSnapshot snapshot)
      throws IOException {
    Path archive = normalize(snapshot.path());
    ModuleArchiveReader.ArchivedModule cached = archives.get(archive);
    if (cached != null) {
      if (cached.archive().equals(snapshot)) return cached;
    }
    ModuleArchiveReader.ArchivedModule loaded = new ModuleArchiveReader().read(archive);
    archives.put(archive, loaded);
    return loaded;
  }

  private record AnalysisModuleKey(Path repositoryRoot, ModuleRequirement requirement) {}
}
