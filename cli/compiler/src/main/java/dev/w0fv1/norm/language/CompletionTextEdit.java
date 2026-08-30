package dev.w0fv1.norm.language;

import dev.w0fv1.norm.value.SourceLocation;
import java.util.Objects;

public record CompletionTextEdit(SourceLocation location, String newText) {
  public CompletionTextEdit {
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(newText, "newText");
  }
}
