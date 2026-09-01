package dev.w0fv1.norm.project;

import java.util.Objects;

public record ModuleResource(String path, byte[] content) {
  public ModuleResource {
    path = Objects.requireNonNull(path, "path").replace('\\', '/');
    if (path.isBlank()
        || path.startsWith("/")
        || path.endsWith("/")
        || path.equals("..")
        || path.startsWith("../")
        || path.contains("/../")) {
      throw new IllegalArgumentException("invalid module resource path: " + path);
    }
    content = Objects.requireNonNull(content, "content").clone();
  }

  @Override
  public byte[] content() {
    return content.clone();
  }
}
