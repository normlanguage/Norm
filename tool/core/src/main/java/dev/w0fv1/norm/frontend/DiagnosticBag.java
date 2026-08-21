package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.Diagnostic;
import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.diagnostic.DiagnosticSeverity;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

final class DiagnosticBag implements Iterable<Diagnostic> {
  private final List<Diagnostic> diagnostics = new ArrayList<>();

  DiagnosticBag() {}

  Diagnostic report(Diagnostic diagnostic) {
    diagnostics.add(Objects.requireNonNull(diagnostic, "diagnostic"));
    return diagnostic;
  }

  Diagnostic error(DiagnosticCode code, String message, SourceSpan span) {
    return report(Diagnostic.error(code, message, span));
  }

  boolean hasErrors() {
    return diagnostics.stream()
        .anyMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
  }

  int size() {
    return diagnostics.size();
  }

  boolean isEmpty() {
    return diagnostics.isEmpty();
  }

  List<Diagnostic> snapshot() {
    return List.copyOf(diagnostics);
  }

  @Override
  public Iterator<Diagnostic> iterator() {
    return snapshot().iterator();
  }
}
