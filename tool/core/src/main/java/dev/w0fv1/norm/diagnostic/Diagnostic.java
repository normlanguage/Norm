package dev.w0fv1.norm.diagnostic;

import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public record Diagnostic(
    DiagnosticCode code,
    DiagnosticSeverity severity,
    String message,
    SourceSpan primarySpan,
    List<RelatedInformation> relatedInformation,
    List<String> notes) {

  public Diagnostic {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(primarySpan, "primarySpan");
    if (message.isBlank()) {
      throw new IllegalArgumentException("diagnostic message must not be blank");
    }
    relatedInformation = List.copyOf(relatedInformation);
    notes = List.copyOf(notes);
    if (notes.stream().anyMatch(String::isBlank)) {
      throw new IllegalArgumentException("diagnostic notes must not be blank");
    }
  }

  public static Diagnostic error(DiagnosticCode code, String message, SourceSpan span) {
    return new Diagnostic(code, DiagnosticSeverity.ERROR, message, span, List.of(), List.of());
  }

  public Diagnostic withNote(String note) {
    Objects.requireNonNull(note, "note");
    var updatedNotes = new java.util.ArrayList<>(notes);
    updatedNotes.add(note);
    return new Diagnostic(code, severity, message, primarySpan, relatedInformation, updatedNotes);
  }

  public Diagnostic withRelatedInformation(RelatedInformation information) {
    Objects.requireNonNull(information, "information");
    var updatedInformation = new java.util.ArrayList<>(relatedInformation);
    updatedInformation.add(information);
    return new Diagnostic(code, severity, message, primarySpan, updatedInformation, notes);
  }
}
