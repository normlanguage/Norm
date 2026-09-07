package dev.w0fv1.norm.cli.component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class NativeApplicationDelivery {
  static List<Path> publish(NativeBuildArtifacts artifacts, Path destination) throws IOException {
    if (!System.getProperty("os.name", "").startsWith("Windows") || artifacts.files().size() == 1) {
      var delivered = artifacts.publishLibraries(destination);
      dev.w0fv1.norm.platform.jdk.FilePublication.publish(artifacts.image(), destination);
      return delivered;
    }
    try (var workspace = new dev.w0fv1.norm.utils.TemporaryDirectory()) {
      Path host = workspace.path().resolve("native-host.exe");
      try (var input = NativeApplicationDelivery.class.getResourceAsStream("/native-host.exe")) {
        if (input == null)
          throw new IOException(
              "The native application host is unavailable in this Norm distribution");
        Files.copy(input, host);
      }
      Path bundle = workspace.path().resolve("application.zip");
      archive(artifacts, bundle);
      new WindowsApplicationExecutable().write(host, bundle, destination);
      return List.of(destination);
    }
  }

  static void archive(NativeBuildArtifacts artifacts, Path destination) throws IOException {
    var names = new HashSet<String>();
    try (var output = new ZipOutputStream(Files.newOutputStream(destination))) {
      for (Path file : artifacts.files()) {
        String name =
            file.equals(artifacts.image())
                ? "application.exe"
                : artifacts.root().relativize(file).toString().replace('\\', '/');
        if (!names.add(name.toLowerCase(java.util.Locale.ROOT)))
          throw new IOException("Duplicate native application entry: " + name);
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        output.putNextEntry(entry);
        Files.copy(file, output);
        output.closeEntry();
      }
    }
  }
}
