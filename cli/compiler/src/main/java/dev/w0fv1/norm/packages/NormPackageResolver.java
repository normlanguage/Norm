package dev.w0fv1.norm.packages;

import dev.w0fv1.norm.platform.jdk.EnvironmentProxySelector;
import dev.w0fv1.norm.value.ModuleArchiveFormat;
import dev.w0fv1.norm.value.ModuleDependency;
import dev.w0fv1.norm.value.ModuleRepositoryCoordinate;
import dev.w0fv1.norm.value.ModuleRepositoryId;
import dev.w0fv1.norm.value.ModuleRequirement;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;

public final class NormPackageResolver implements AutoCloseable {
  private final Path localRepository;
  private final Path cache;
  private final Map<ModuleRepositoryId, NormPackageRepository> repositories;
  private final HttpClient client;

  public record ResolutionInputs(
      java.util.List<dev.w0fv1.norm.value.FileSnapshot> files, java.util.List<Path> absent) {
    public ResolutionInputs {
      files = java.util.List.copyOf(files);
      absent = java.util.List.copyOf(absent);
    }
  }

  public record ResolvedPackage(Path archive, ResolutionInputs inputs) {
    public ResolvedPackage {
      Objects.requireNonNull(archive, "archive");
      Objects.requireNonNull(inputs, "inputs");
    }
  }

  public NormPackageResolver(Path cache) {
    this(cache, cache, defaultRepositories());
  }

  public NormPackageResolver(Path localRepository, Path cache) {
    this(localRepository, cache, defaultRepositories());
  }

  NormPackageResolver(
      Path localRepository,
      Path cache,
      Map<ModuleRepositoryId, NormPackageRepository> repositories) {
    this.localRepository = normalize(Objects.requireNonNull(localRepository, "localRepository"));
    this.cache = normalize(Objects.requireNonNull(cache, "cache"));
    this.repositories = Map.copyOf(repositories);
    client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .proxy(EnvironmentProxySelector.system())
            .build();
  }

  public ResolvedPackage resolve(ModuleRequirement requirement) throws IOException {
    Objects.requireNonNull(requirement, "requirement");
    Path relative = relativePath(requirement);
    Path local = localRepository.resolve(relative);
    Path cached = cache.resolve(requirement.repository().value()).resolve(relative);
    try {
      if (Files.isRegularFile(local)) {
        return new ResolvedPackage(
            normalize(local),
            new ResolutionInputs(
                java.util.List.of(dev.w0fv1.norm.value.FileSnapshot.capture(local)),
                java.util.List.of()));
      }
      var cachedInputs = cachedArtifactInputs(cached);
      if (!cachedInputs.isEmpty()) {
        return new ResolvedPackage(
            normalize(cached),
            new ResolutionInputs(cachedInputs, java.util.List.of(normalize(local))));
      }
      NormPackageRepository repository = repositories.get(requirement.repository());
      if (repository == null) {
        throw new IOException(
            "unknown Norm package repository '" + requirement.repository().value() + "'");
      }
      URI archiveUri = repository.locate(requirement, client);
      URI digestUri = URI.create(archiveUri + ".sha256");
      Sha256Digest expected = publishedDigest(digestUri, requirement);
      Files.createDirectories(cached.getParent());
      Path temporary =
          Files.createTempFile(cached.getParent(), cached.getFileName().toString(), ".part");
      try {
        download(archiveUri, temporary, requirement);
        Sha256Digest actual = Sha256Digest.compute(temporary);
        if (!expected.equals(actual)) {
          throw new IOException(
              "Norm package integrity mismatch for "
                  + display(requirement)
                  + ": expected "
                  + expected
                  + ", actual "
                  + actual);
        }
        move(temporary, cached);
        Files.writeString(
            digestPath(cached), expected.value() + System.lineSeparator(), StandardCharsets.UTF_8);
        var captured =
            java.util.List.of(
                new dev.w0fv1.norm.value.FileSnapshot(cached, actual),
                new dev.w0fv1.norm.value.FileSnapshot(
                    digestPath(cached),
                    Sha256Digest.compute(
                        (expected.value() + System.lineSeparator())
                            .getBytes(StandardCharsets.UTF_8))));
        return new ResolvedPackage(
            normalize(cached), new ResolutionInputs(captured, java.util.List.of(normalize(local))));
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException exception) {
      var files = new java.util.ArrayList<dev.w0fv1.norm.value.FileSnapshot>();
      var absent = new java.util.ArrayList<Path>();
      for (Path path : java.util.List.of(local, cached, digestPath(cached))) {
        if (Files.isRegularFile(path)) files.add(dev.w0fv1.norm.value.FileSnapshot.capture(path));
        else absent.add(normalize(path));
      }
      throw new PackageResolutionException(exception, new ResolutionInputs(files, absent));
    }
  }

  public ModuleRequirement resolveReference(
      ModuleRepositoryId repositoryId, String path, int version) throws IOException {
    NormPackageRepository repository = repositories.get(repositoryId);
    if (repository == null)
      throw new IOException("unknown Norm package repository '" + repositoryId.value() + "'");
    String name =
        repository.moduleNames(client).stream()
            .filter(candidate -> path.startsWith(candidate + "."))
            .max(java.util.Comparator.comparingInt(String::length))
            .orElseThrow(
                () ->
                    new IOException(
                        "no registered module owns reference '"
                            + path
                            + "' in repository '"
                            + repositoryId.value()
                            + "'"));
    return new ModuleRequirement(repositoryId.value(), name, version, false);
  }

  public ModuleRequirement resolve(ModuleDependency dependency) throws IOException {
    Objects.requireNonNull(dependency, "dependency");
    if (dependency.version().isPresent()) {
      return dependency.resolved(dependency.version().getAsInt());
    }
    NormPackageRepository repository = repositories.get(dependency.repository());
    if (repository == null) {
      throw new IOException(
          "unknown Norm package repository '" + dependency.repository().value() + "'");
    }
    return dependency.resolved(repository.latestVersion(dependency.name(), client));
  }

  private static java.util.List<dev.w0fv1.norm.value.FileSnapshot> cachedArtifactInputs(
      Path archive) throws IOException {
    Path digest = digestPath(archive);
    if (!Files.isRegularFile(archive) || !Files.isRegularFile(digest)) return java.util.List.of();
    String text = Files.readString(digest, StandardCharsets.UTF_8);
    Sha256Digest expected;
    try {
      expected = parseDigest(text);
    } catch (IllegalArgumentException exception) {
      throw new IOException("invalid cached package integrity metadata: " + digest, exception);
    }
    Sha256Digest actual = Sha256Digest.compute(archive);
    return expected.equals(actual)
        ? java.util.List.of(
            new dev.w0fv1.norm.value.FileSnapshot(archive, actual),
            new dev.w0fv1.norm.value.FileSnapshot(
                digest, Sha256Digest.compute(text.getBytes(StandardCharsets.UTF_8))))
        : java.util.List.of();
  }

  private Sha256Digest publishedDigest(URI uri, ModuleRequirement requirement) throws IOException {
    try {
      String value;
      if (uri.getScheme().equalsIgnoreCase("file")) {
        value = Files.readString(Path.of(uri), StandardCharsets.UTF_8);
      } else {
        HttpResponse<String> response =
            RepositoryHttp.send(
                client,
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
          throw unavailable(requirement, response.statusCode());
        }
        value = response.body();
      }
      return parseDigest(value);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while resolving " + display(requirement), exception);
    } catch (IllegalArgumentException exception) {
      throw new IOException("invalid published digest for " + display(requirement), exception);
    }
  }

  private void download(URI uri, Path target, ModuleRequirement requirement) throws IOException {
    try {
      if (uri.getScheme().equalsIgnoreCase("file")) {
        Files.copy(Path.of(uri), target, StandardCopyOption.REPLACE_EXISTING);
        return;
      }
      HttpResponse<Path> response =
          RepositoryHttp.send(
              client,
              HttpRequest.newBuilder(uri).GET().build(),
              HttpResponse.BodyHandlers.ofFile(target));
      if (response.statusCode() != 200) throw unavailable(requirement, response.statusCode());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while resolving " + display(requirement), exception);
    }
  }

  private static IOException unavailable(ModuleRequirement requirement, int statusCode) {
    return new IOException(
        "cannot resolve Norm package " + display(requirement) + ": HTTP " + statusCode);
  }

  private static Sha256Digest parseDigest(String value) {
    String trimmed = value.trim();
    int separator = trimmed.indexOf(' ');
    return Sha256Digest.parse(separator < 0 ? trimmed : trimmed.substring(0, separator));
  }

  private static Path relativePath(ModuleRequirement requirement) {
    ModuleRepositoryCoordinate coordinate =
        ModuleRepositoryCoordinate.from(requirement.coordinate());
    return Path.of(coordinate.group().replace('.', java.io.File.separatorChar))
        .resolve(coordinate.artifact())
        .resolve(coordinate.version())
        .resolve(
            coordinate.artifact() + "-" + coordinate.version() + ModuleArchiveFormat.FILE_SUFFIX);
  }

  private static Path digestPath(Path archive) {
    return archive.resolveSibling(archive.getFileName() + ".sha256");
  }

  private static String display(ModuleRequirement requirement) {
    return requirement.repository().value()
        + ":"
        + requirement.name()
        + "@"
        + requirement.version();
  }

  private static void move(Path source, Path target) throws IOException {
    try {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static Path normalize(Path path) {
    return path.toAbsolutePath().normalize();
  }

  private static Map<ModuleRepositoryId, NormPackageRepository> defaultRepositories() {
    return Map.of(ModuleRepositoryId.GITHUB, new GitHubPackageRepository());
  }

  @Override
  public void close() {
    client.close();
  }
}
