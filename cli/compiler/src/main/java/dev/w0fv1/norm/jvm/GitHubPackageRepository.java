package dev.w0fv1.norm.jvm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleRepositoryCoordinate;
import dev.w0fv1.norm.value.ModuleRequirement;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class GitHubPackageRepository implements NormPackageRepository {
  static final URI REGISTRY =
      URI.create("https://raw.githubusercontent.com/normlanguage/registry/main/registry.json");
  static final URI RELEASES = URI.create("https://github.com/");

  private final URI registryUri;
  private final URI releasesUri;
  private volatile Map<String, GitHubRepository> packages;

  GitHubPackageRepository() {
    this(REGISTRY, RELEASES);
  }

  GitHubPackageRepository(URI registryUri, URI releasesUri) {
    this.registryUri = Objects.requireNonNull(registryUri, "registryUri");
    this.releasesUri = Objects.requireNonNull(releasesUri, "releasesUri");
  }

  @Override
  public URI locate(ModuleRequirement requirement, HttpClient client) throws IOException {
    GitHubRepository repository = registry(client).get(requirement.name());
    if (repository == null) {
      throw new IOException(
          "Norm module '" + requirement.name() + "' is not registered in repository 'github'");
    }
    ModuleRepositoryCoordinate coordinate =
        ModuleRepositoryCoordinate.from(requirement.coordinate());
    return releasesUri.resolve(
        repository.owner()
            + "/"
            + repository.repository()
            + "/releases/download/v"
            + requirement.version()
            + "/"
            + coordinate.artifact()
            + "-"
            + coordinate.version()
            + ".nar");
  }

  private Map<String, GitHubRepository> registry(HttpClient client) throws IOException {
    Map<String, GitHubRepository> current = packages;
    if (current != null) return current;
    synchronized (this) {
      current = packages;
      if (current == null) {
        current = readRegistry(client);
        packages = current;
      }
    }
    return current;
  }

  private Map<String, GitHubRepository> readRegistry(HttpClient client) throws IOException {
    try {
      JsonObject root = JsonParser.parseString(read(registryUri, client)).getAsJsonObject();
      if (root.get("formatVersion").getAsInt() != 1) {
        throw new IllegalArgumentException("unsupported registry format");
      }
      Map<String, GitHubRepository> result = new LinkedHashMap<>();
      root.getAsJsonArray("packages")
          .forEach(
              value -> {
                JsonObject entry = value.getAsJsonObject();
                String name = entry.get("name").getAsString();
                new ModuleCoordinate(name, 1);
                GitHubRepository repository =
                    new GitHubRepository(
                        entry.get("owner").getAsString(), entry.get("repository").getAsString());
                if (result.putIfAbsent(name, repository) != null) {
                  throw new IllegalArgumentException("duplicate registered module '" + name + "'");
                }
              });
      return Map.copyOf(result);
    } catch (RuntimeException exception) {
      throw new IOException("invalid GitHub package registry " + registryUri, exception);
    }
  }

  private static String read(URI uri, HttpClient client) throws IOException {
    if (uri.getScheme().equalsIgnoreCase("file")) {
      return Files.readString(Path.of(uri), StandardCharsets.UTF_8);
    }
    try {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(uri).GET().build(),
              HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        throw new IOException(
            "cannot load GitHub package registry " + uri + ": HTTP " + response.statusCode());
      }
      return response.body();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while loading GitHub package registry", exception);
    }
  }

  private record GitHubRepository(String owner, String repository) {
    private GitHubRepository {
      requireSlug(owner, "owner");
      requireSlug(repository, "repository");
    }

    private static void requireSlug(String value, String role) {
      Objects.requireNonNull(value, role);
      if (!value.matches("[A-Za-z0-9](?:[A-Za-z0-9_.-]*[A-Za-z0-9])?")) {
        throw new IllegalArgumentException("invalid GitHub " + role + " '" + value + "'");
      }
    }
  }
}
