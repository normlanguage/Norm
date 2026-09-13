package dev.w0fv1.norm.build;

import com.google.gson.JsonObject;
import dev.w0fv1.norm.application.CompiledApplication;
import dev.w0fv1.norm.application.PreparedApplicationWriter;
import dev.w0fv1.norm.application.TemporaryDirectory;
import dev.w0fv1.norm.platform.jdk.FilePublication;
import dev.w0fv1.norm.value.ApplicationBundleFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ApplicationBundleWriter {
  Path write(CompiledApplication application, Path destination) throws IOException {
    Path output = destination.toAbsolutePath().normalize();
    try (var workspace = new TemporaryDirectory()) {
      Path root = workspace.path().resolve("bundle");
      writeDirectory(application, root);
      Path temporary = workspace.path().resolve("application.zip");
      try (var archive = new ZipOutputStream(Files.newOutputStream(temporary));
          var files = Files.walk(root)) {
        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
          var entry = new ZipEntry(root.relativize(file).toString().replace('\\', '/'));
          entry.setTime(0);
          archive.putNextEntry(entry);
          Files.copy(file, archive);
          archive.closeEntry();
        }
      }
      FilePublication.publish(temporary, output);
    }
    return output;
  }

  Path writeDirectory(CompiledApplication application, Path directory) throws IOException {
    Path entry = new PreparedApplicationWriter().write(application, directory);
    var descriptor = new JsonObject();
    descriptor.addProperty("formatVersion", ApplicationBundleFormat.FORMAT_VERSION);
    descriptor.addProperty("entry", directory.relativize(entry).toString().replace('\\', '/'));
    Files.writeString(directory.resolve(ApplicationBundleFormat.DESCRIPTOR), descriptor.toString());
    return entry;
  }
}
