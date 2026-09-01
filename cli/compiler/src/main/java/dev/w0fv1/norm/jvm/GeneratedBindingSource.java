package dev.w0fv1.norm.jvm;

import java.util.List;
import java.util.Objects;

public record GeneratedBindingSource(String relativePath, String text, List<String> callIds) {
  public GeneratedBindingSource {
    Objects.requireNonNull(relativePath, "relativePath");
    Objects.requireNonNull(text, "text");
    callIds = List.copyOf(callIds);
  }
}
