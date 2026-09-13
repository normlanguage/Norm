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
      Path repositoryRoot, ModuleRequirement requirement, ProjectLoadPurpose purpose)
      throws IOException {
    AnalysisModuleKey analysisKey = new AnalysisModuleKey(normalize(repositoryRoot), requirement);
    if (purpose == ProjectLoadPurpose.ANALYSIS) {
      ResolvedProjectModule cached = analysisModules.get(analysisKey);
      if (cached != null) {
        cached.archive().orElseThrow().verify();
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
    Path archive = packages.resolve(requirement);
    progress.accept("Using NAR: " + archive);
    ModuleArchiveReader.ArchivedModule archived = archive(archive);
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
        ResolvedJarGraph graph = jars.resolve(repositoryRoot, descriptor.binding().orElseThrow());
        progress.accept(
            "Adapting " + graph.artifacts().size() + " Java artifacts for " + requirement.name());
        ResolvedJarBinding resolved = JarBindingPreparer.prepareArchived(descriptor, graph);
        Map<String, String> expected = new LinkedHashMap<>();
        for (GeneratedBindingSource source : resolved.generated().sources()) {
          expected.put(source.relativePath(), source.text());
        }
        generatedSources = Map.copyOf(expected);
        binding = Optional.of(resolved);
      } else {
        Map<String, String> expected = new LinkedHashMap<>();
        int bindingExports = descriptor.binding().orElseThrow().api().size();
        for (String exported : descriptor.exports().subList(0, bindingExports)) {
          String path = descriptor.sourcePath(exported);
          String source = archived.sources().get(path);
          if (source == null) throw new IOException("module binding source is absent: " + path);
          expected.put(path, source);
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
            ResolvedProjectModule.exportedSources(loaded, bindingSources),
            bindingSources,
            binding,
            archived.resources(),
            Optional.of(archived.archive()),
            Set.of(),
            archived.publicTypes());
    if (purpose != ProjectLoadPurpose.ANALYSIS) return result;
    ResolvedProjectModule cached = analysisModules.putIfAbsent(analysisKey, result);
    return cached == null ? result : cached;
  }

  private ModuleArchiveReader.ArchivedModule archive(Path path) throws IOException {
    Path archive = normalize(path);
    ModuleArchiveReader.ArchivedModule cached = archives.get(archive);
    if (cached != null) {
      cached.archive().verify();
      return cached;
    }
    ModuleArchiveReader.ArchivedModule loaded = new ModuleArchiveReader().read(archive);
    ModuleArchiveReader.ArchivedModule existing = archives.putIfAbsent(archive, loaded);
    return existing == null ? loaded : existing;
  }

  private record AnalysisModuleKey(Path repositoryRoot, ModuleRequirement requirement) {}
}
