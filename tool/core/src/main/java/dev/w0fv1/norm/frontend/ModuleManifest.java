package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.SourceFile;
import java.util.List;
import java.util.Objects;

public record ModuleManifest(SourceFile source, String name, int version, List<String> exports) {
  public ModuleManifest {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(name, "name");
    exports = List.copyOf(exports);
  }

  public String sourcePath(String exportedName) {
    return (name + "." + exportedName).replace('.', '/') + ".norm";
  }

  public String sourcePackage(String exportedName) {
    String qualified = name + "." + exportedName;
    return qualified.substring(0, qualified.lastIndexOf('.'));
  }

  public static boolean isManifest(SourceFile source) {
    String path = source.id().uri().getPath();
    if (path == null || path.isEmpty()) path = source.displayName();
    path = path.replace('\\', '/');
    return path.substring(path.lastIndexOf('/') + 1).equals("module.norm");
  }
}
