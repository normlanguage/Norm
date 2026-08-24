package dev.w0fv1.norm.value;

import java.net.URI;
import java.util.Objects;

public record CompilationUnitId(URI uri) implements Comparable<CompilationUnitId> {
  public CompilationUnitId {
    Objects.requireNonNull(uri, "uri");
    if (!uri.isAbsolute())
      throw new IllegalArgumentException("compilation unit URI must be absolute");
  }

  public static CompilationUnitId of(String uri) {
    return new CompilationUnitId(URI.create(uri));
  }

  @Override
  public int compareTo(CompilationUnitId other) {
    return uri.toString().compareTo(Objects.requireNonNull(other, "other").uri.toString());
  }

  @Override
  public String toString() {
    return uri.toString();
  }
}
