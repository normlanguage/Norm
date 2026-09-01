package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.LocalJarTarget;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.ModuleArchiveFormat;
import dev.w0fv1.norm.value.ModuleRepositoryCoordinate;
import dev.w0fv1.norm.value.ModuleRequirement;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;

public final class JarResolver implements AutoCloseable {
  private static final List<RemoteRepository> REPOSITORIES =
      List.of(
          new RemoteRepository.Builder(
                  "central", "default", "https://repo.maven.apache.org/maven2/")
              .build());

  private final RepositorySystem repositorySystem;
  private final RepositorySystemSession.CloseableSession moduleSession;
  private final RepositorySystemSession.CloseableSession jarSession;

  public JarResolver(Path cacheDirectory) {
    this(cacheDirectory, cacheDirectory);
  }

  public JarResolver(Path moduleRepository, Path jarCache) {
    Objects.requireNonNull(moduleRepository, "moduleRepository");
    Objects.requireNonNull(jarCache, "jarCache");
    repositorySystem = new RepositorySystemSupplier().get();
    SessionBuilderSupplier supplier = new SessionBuilderSupplier(repositorySystem);
    moduleSession = session(supplier, moduleRepository);
    if (moduleRepository
        .toAbsolutePath()
        .normalize()
        .equals(jarCache.toAbsolutePath().normalize())) {
      jarSession = moduleSession;
    } else {
      jarSession = session(supplier, jarCache);
    }
  }

  private static RepositorySystemSession.CloseableSession session(
      SessionBuilderSupplier supplier, Path localRepository) {
    RepositorySystemSession.SessionBuilder builder = supplier.get();
    builder.setDependencySelector(
        new AndDependencySelector(
            supplier.getDependencySelector(), NonOptionalDependencySelector.INSTANCE));
    builder.withLocalRepositories(
        new LocalRepository(localRepository.toAbsolutePath().normalize()));
    return builder.build();
  }

  public ResolvedJarGraph resolve(Path moduleRoot, JarBinding binding) throws IOException {
    Objects.requireNonNull(moduleRoot, "moduleRoot");
    Objects.requireNonNull(binding, "binding");
    return switch (binding.target()) {
      case LocalJarTarget target -> resolveLocal(moduleRoot, target);
      case MavenJarTarget target -> resolveMaven(target);
    };
  }

  public Path resolveModuleArchive(ModuleRequirement requirement) throws IOException {
    ModuleRepositoryCoordinate coordinate =
        ModuleRepositoryCoordinate.from(requirement.coordinate());
    Artifact artifact =
        new DefaultArtifact(
            coordinate.group(),
            coordinate.artifact(),
            "",
            ModuleArchiveFormat.EXTENSION,
            coordinate.version());
    try {
      ArtifactRequest request = new ArtifactRequest(artifact, REPOSITORIES, null);
      Path path = repositorySystem.resolveArtifact(moduleSession, request).getArtifact().getPath();
      if (path == null || !Files.isRegularFile(path)) {
        throw new IOException(
            "resolved Norm module has no artifact content: " + requirement.name());
      }
      return path.toAbsolutePath().normalize();
    } catch (ArtifactResolutionException exception) {
      throw new IOException(
          "cannot resolve Norm module " + requirement.name() + "@" + requirement.version(),
          exception);
    }
  }

  private static ResolvedJarGraph resolveLocal(Path moduleRoot, LocalJarTarget target)
      throws IOException {
    Path root = moduleRoot.toAbsolutePath().normalize();
    Path file = root.resolve(target.path()).normalize();
    if (!file.startsWith(root) || !Files.isRegularFile(file)) {
      throw new IOException("local JAR does not exist inside the module: " + target.path());
    }
    Sha256Digest content = Sha256Digest.compute(file);
    if (target.integrity().isPresent() && !target.integrity().orElseThrow().equals(content)) {
      throw new IOException(
          "local JAR integrity mismatch for "
              + target.path()
              + ": expected "
              + target.integrity().orElseThrow()
              + ", actual "
              + content);
    }
    ResolvedJarArtifact artifact =
        new ResolvedJarArtifact(new LocalJarIdentity(content), file, content);
    return new ResolvedJarGraph(artifact, List.of(artifact), List.of());
  }

  private ResolvedJarGraph resolveMaven(MavenJarTarget target) throws IOException {
    MavenArtifactCoordinate coordinate = target.coordinate();
    Artifact rootArtifact =
        new DefaultArtifact(
            coordinate.group(), coordinate.artifact(), "", "jar", coordinate.version());
    CollectRequest collect =
        new CollectRequest(new Dependency(rootArtifact, JavaScopes.RUNTIME), REPOSITORIES);
    DependencyRequest request =
        new DependencyRequest(collect, DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME));
    try {
      DependencyResult result = repositorySystem.resolveDependencies(jarSession, request);
      ResolvedJarGraph graph = graph(result.getRoot());
      if (target.resolution().isPresent()
          && !target.resolution().orElseThrow().equals(graph.contentId())) {
        throw new IOException(
            "Maven JAR resolution mismatch for "
                + coordinate.notation()
                + ": expected "
                + target.resolution().orElseThrow()
                + ", actual "
                + graph.contentId());
      }
      return graph;
    } catch (DependencyResolutionException exception) {
      throw new IOException("cannot resolve Maven JAR " + coordinate.notation(), exception);
    }
  }

  private static ResolvedJarGraph graph(DependencyNode rootNode) throws IOException {
    Map<JarArtifactIdentity, ResolvedJarArtifact> artifacts = new LinkedHashMap<>();
    List<JarDependencyEdge> edges = new ArrayList<>();
    collect(rootNode, artifacts, edges);
    JarArtifactIdentity rootIdentity = identity(rootNode.getArtifact());
    ResolvedJarArtifact root = artifacts.get(rootIdentity);
    if (root == null) throw new IOException("resolved Maven graph has no root JAR");
    return new ResolvedJarGraph(root, List.copyOf(artifacts.values()), edges);
  }

  private static void collect(
      DependencyNode node,
      Map<JarArtifactIdentity, ResolvedJarArtifact> artifacts,
      List<JarDependencyEdge> edges)
      throws IOException {
    Artifact artifact = node.getArtifact();
    JarArtifactIdentity current = identity(artifact);
    Path file = artifact.getPath();
    if (file == null || !Files.isRegularFile(file)) {
      throw new IOException("resolved Maven artifact has no JAR content: " + artifact);
    }
    artifacts.putIfAbsent(
        current, new ResolvedJarArtifact(current, file, Sha256Digest.compute(file)));
    for (DependencyNode child : node.getChildren()) {
      JarArtifactIdentity childIdentity = identity(child.getArtifact());
      if (!current.equals(childIdentity)) edges.add(new JarDependencyEdge(current, childIdentity));
      collect(child, artifacts, edges);
    }
  }

  private static MavenJarIdentity identity(Artifact artifact) throws IOException {
    if (artifact == null || !artifact.getExtension().equals("jar")) {
      throw new IOException("resolved dependency is not a JAR: " + artifact);
    }
    return new MavenJarIdentity(
        new MavenArtifactCoordinate(
            artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion()),
        artifact.getClassifier());
  }

  @Override
  public void close() {
    if (moduleSession != jarSession) moduleSession.close();
    jarSession.close();
    repositorySystem.shutdown();
  }

  private enum NonOptionalDependencySelector implements DependencySelector {
    INSTANCE;

    @Override
    public boolean selectDependency(Dependency dependency) {
      return !dependency.isOptional();
    }

    @Override
    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
      return this;
    }
  }
}
