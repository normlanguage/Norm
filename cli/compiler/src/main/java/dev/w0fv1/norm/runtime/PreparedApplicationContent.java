package dev.w0fv1.norm.runtime;

import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.value.FileSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record PreparedApplicationContent(
    ApplicationProgramData program, Map<String, byte[]> classes, List<FileSnapshot> dependencies) {
  public PreparedApplicationContent {
    classes = Map.copyOf(classes);
    dependencies = List.copyOf(dependencies);
  }

  public Path materialize(Path directory) throws IOException {
    var application = prepare(directory);
    Path entry = directory.resolve(PreparedApplication.ENTRY);
    PortableObjectCodec.write(application, entry);
    return entry;
  }

  public PreparedApplication prepare(Path directory) throws IOException {
    Path root = directory.toAbsolutePath().normalize();
    Path classRoot = Files.createDirectories(root.resolve("classes"));
    for (var entry : classes.entrySet()) {
      Path target = classRoot.resolve(entry.getKey()).normalize();
      if (!target.startsWith(classRoot))
        throw new IOException("prepared resource is outside its directory");
      Files.createDirectories(target.getParent());
      Files.write(target, entry.getValue());
    }
    var paths = new ArrayList<String>();
    paths.add("classes");
    for (var dependency : dependencies) {
      String path = "jars/" + paths.size() + "-" + dependency.path().getFileName();
      dependency.copyTo(root.resolve(path));
      paths.add(path);
    }
    return new PreparedApplication(program, paths);
  }
}
