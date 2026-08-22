package dev.w0fv1.norm.diagnostic;

import dev.w0fv1.norm.value.SourcePosition;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.Locale;
import java.util.Objects;

public final class DiagnosticRenderer {
  private DiagnosticRenderer() {}

  public static String render(Diagnostic diagnostic) {
    Objects.requireNonNull(diagnostic, "diagnostic");
    SourceSpan span = diagnostic.primarySpan();
    SourcePosition start = span.start();
    String line = span.source().lineText(start.line());

    StringBuilder rendered = new StringBuilder();
    rendered
        .append(span.source().displayName())
        .append(':')
        .append(start.line())
        .append(':')
        .append(start.column())
        .append(": ")
        .append(diagnostic.severity().name().toLowerCase(Locale.ROOT))
        .append('[')
        .append(diagnostic.code())
        .append("]: ")
        .append(diagnostic.message())
        .append(System.lineSeparator());

    rendered.append(line).append(System.lineSeparator());
    rendered.append(" ".repeat(Math.max(0, start.column() - 1)));
    rendered.append("^".repeat(highlightWidth(span, line, start)));

    for (RelatedInformation information : diagnostic.relatedInformation()) {
      SourcePosition relatedStart = information.span().start();
      rendered
          .append(System.lineSeparator())
          .append("related: ")
          .append(information.span().source().displayName())
          .append(':')
          .append(relatedStart.line())
          .append(':')
          .append(relatedStart.column())
          .append(": ")
          .append(information.message());
    }
    for (String note : diagnostic.notes()) {
      rendered.append(System.lineSeparator()).append("note: ").append(note);
    }
    return rendered.toString();
  }

  private static int highlightWidth(SourceSpan span, String line, SourcePosition start) {
    if (span.isEmpty() || span.end().line() != start.line()) {
      return 1;
    }
    int available = Math.max(1, line.length() - start.column() + 2);
    return Math.min(Math.max(1, span.length()), available);
  }
}
