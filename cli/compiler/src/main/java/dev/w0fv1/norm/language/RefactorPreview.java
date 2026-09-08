package dev.w0fv1.norm.language;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.source.SourceLocation;
import java.util.List;
import java.util.Objects;

public record RefactorPreview(
    List<DocumentRevision> inputs,
    List<DocumentChange> changes,
    List<Diagnostic> before,
    List<Diagnostic> after) {
  public RefactorPreview {
    inputs = List.copyOf(inputs);
    changes = List.copyOf(changes);
    before = List.copyOf(before);
    after = List.copyOf(after);
  }

  public record DocumentChange(
      DocumentRevision before, DocumentRevision after, List<Replacement> edits) {
    public DocumentChange {
      Objects.requireNonNull(before, "before");
      Objects.requireNonNull(after, "after");
      edits = List.copyOf(edits);
      if (!before.document().equals(after.document())
          || edits.stream().anyMatch(edit -> !edit.location().document().equals(before.document())))
        throw new IllegalArgumentException("document change locations must have one identity");
    }
  }

  public record Replacement(SourceLocation location, String oldText, String newText) {
    public Replacement {
      Objects.requireNonNull(location, "location");
      Objects.requireNonNull(oldText, "oldText");
      Objects.requireNonNull(newText, "newText");
      if (location.length() != oldText.length())
        throw new IllegalArgumentException("replacement text must match its source range");
    }
  }
}
