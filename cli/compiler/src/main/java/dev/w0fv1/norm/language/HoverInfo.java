package dev.w0fv1.norm.language;

import dev.w0fv1.norm.value.SourceLocation;
import java.util.Objects;
import java.util.Optional;

public record HoverInfo(String markdown, Optional<SourceLocation> location) {
  public HoverInfo {
    Objects.requireNonNull(markdown, "markdown");
    location = Objects.requireNonNull(location, "location");
  }
}
