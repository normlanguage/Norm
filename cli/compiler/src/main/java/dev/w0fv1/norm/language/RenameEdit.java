package dev.w0fv1.norm.language;

import dev.w0fv1.norm.value.SourceLocation;
import java.util.List;
import java.util.Objects;

public record RenameEdit(String newName, List<SourceLocation> locations) {
  public RenameEdit {
    Objects.requireNonNull(newName, "newName");
    locations = List.copyOf(locations);
  }
}
