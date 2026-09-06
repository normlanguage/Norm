package dev.w0fv1.norm.jvm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;

public record JarServiceIndex(List<Registration> registrations) {
  public JarServiceIndex {
    registrations = List.copyOf(registrations);
  }

  public static JarServiceIndex scan(List<Path> classpath) throws IOException {
    var registrations = new LinkedHashSet<Registration>();
    var paths = new LinkedHashSet<Path>();
    classpath.forEach(path -> paths.add(path.toAbsolutePath().normalize()));
    for (Path path : paths) {
      try (var archive = new JarFile(path.toFile())) {
        var entries = archive.entries();
        while (entries.hasMoreElements()) {
          var entry = entries.nextElement();
          String prefix = "META-INF/services/";
          if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
          String service = entry.getName().substring(prefix.length());
          if (service.isEmpty() || service.contains("/")) continue;
          try (var reader =
              new BufferedReader(
                  new InputStreamReader(archive.getInputStream(entry), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
              int comment = line.indexOf('#');
              String provider = (comment < 0 ? line : line.substring(0, comment)).strip();
              if (!provider.isEmpty()) registrations.add(new Registration(path, service, provider));
            }
          }
        }
      }
    }
    return new JarServiceIndex(List.copyOf(registrations));
  }

  public record Registration(Path artifact, String service, String provider) {
    public Registration {
      artifact = Objects.requireNonNull(artifact, "artifact").toAbsolutePath().normalize();
      Objects.requireNonNull(service, "service");
      Objects.requireNonNull(provider, "provider");
      if (service.isBlank() || provider.isBlank())
        throw new IllegalArgumentException("service and provider must not be blank");
    }
  }
}
