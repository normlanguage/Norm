package dev.w0fv1.norm.language;

import dev.w0fv1.norm.value.SourceLocation;
import java.util.Objects;

public record RenameTarget(SourceLocation location, String placeholder) {
  public RenameTarget {
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(placeholder, "placeholder");
  }
}
