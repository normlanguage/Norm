package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.ModuleArchiveFormat;
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
  private static final Map<ModuleRepositoryId, URI> DEFAULT_REPOSITORIES =
      Map.of(
          ModuleRepositoryId.GITHUB, URI.create("https://normlanguage.github.io/Norm/repository/"));

  private final Path localRepository;
  private final Path cache;
  private final Map<ModuleRepositoryId, URI> repositories;
  private final HttpClient client;

  public NormPackageResolver(Path cache) {
    this(cache, cache, DEFAULT_REPOSITORIES);
  }

  public NormPackageResolver(Path localRepository, Path cache) {
    this(localRepository, cache, DEFAULT_REPOSITORIES);
  }

  NormPackageResolver(Path localRepository, Path cache, Map<ModuleRepositoryId, URI> repositories) {
    this.localRepository = normalize(Objects.requireNonNull(localRepository, "localRepository"));
    this.cache = normalize(Objects.requireNonNull(cache, "cache"));
    this.repositories = Map.copyOf(repositories);
    client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
  }

  public Path resolve(ModuleRequirement requirement) throws IOException {
    Objects.requireNonNull(requirement, "requirement");
    Path relative = relativePath(requirement);
    Path local = localRepository.resolve(relative);
    if (Files.isRegularFile(local)) return normalize(local);
    Path cached = cache.resolve(requirement.repository().value()).resolve(relative);
    if (validCachedArtifact(cached)) return normalize(cached);
    URI repository = repositories.get(requirement.repository());
    if (repository == null) {
      throw new IOException(
          "unknown Norm package repository '" + requirement.repository().value() + "'");
    }
    URI archiveUri = repository.resolve(relative.toString().replace('\\', '/'));
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
      return normalize(cached);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static boolean validCachedArtifact(Path archive) throws IOException {
    Path digest = digestPath(archive);
    if (!Files.isRegularFile(archive) || !Files.isRegularFile(digest)) return false;
    Sha256Digest expected = parseDigest(Files.readString(digest, StandardCharsets.UTF_8));
    return expected.equals(Sha256Digest.compute(archive));
  }

  private Sha256Digest publishedDigest(URI uri, ModuleRequirement requirement) throws IOException {
    try {
      String value;
      if (uri.getScheme().equalsIgnoreCase("file")) {
        value = Files.readString(Path.of(uri), StandardCharsets.UTF_8);
      } else {
        HttpResponse<String> response =
            client.send(
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
          client.send(
              HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofFile(target));
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

  @Override
  public void close() {
    client.close();
  }
}
