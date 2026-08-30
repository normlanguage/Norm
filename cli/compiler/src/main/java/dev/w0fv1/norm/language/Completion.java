package dev.w0fv1.norm.language;

import java.util.List;
import java.util.Objects;

public record Completion(
    String label,
    CompletionKind kind,
    String detail,
    String documentation,
    String insertText,
    boolean snippet,
    java.util.Optional<CompletionTextEdit> textEdit,
    List<CompletionTextEdit> additionalTextEdits) {
  public Completion {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(detail, "detail");
    Objects.requireNonNull(documentation, "documentation");
    Objects.requireNonNull(insertText, "insertText");
    textEdit = Objects.requireNonNull(textEdit, "textEdit");
    additionalTextEdits = List.copyOf(additionalTextEdits);
  }

  public Completion(
      String label,
      CompletionKind kind,
      String detail,
      String documentation,
      String insertText,
      boolean snippet) {
    this(
        label,
        kind,
        detail,
        documentation,
        insertText,
        snippet,
        java.util.Optional.empty(),
        List.of());
  }

  public Completion(
      String label,
      CompletionKind kind,
      String detail,
      String documentation,
      String insertText,
      boolean snippet,
      List<CompletionTextEdit> additionalTextEdits) {
    this(
        label,
        kind,
        detail,
        documentation,
        insertText,
        snippet,
        java.util.Optional.empty(),
        additionalTextEdits);
  }

  public Completion withTextEdit(CompletionTextEdit edit) {
    return new Completion(
        label,
        kind,
        detail,
        documentation,
        insertText,
        snippet,
        java.util.Optional.of(edit),
        additionalTextEdits);
  }
}
