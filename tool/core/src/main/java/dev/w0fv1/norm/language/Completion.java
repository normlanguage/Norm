package dev.w0fv1.norm.language;

import java.util.Objects;

public record Completion(
    String label,
    CompletionKind kind,
    String detail,
    String documentation,
    String insertText,
    boolean snippet) {
  public Completion {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(documentation, "documentation");
    Objects.requireNonNull(insertText, "insertText");
  }
}
