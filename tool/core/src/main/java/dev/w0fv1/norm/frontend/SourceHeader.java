package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.SourceFile;
import java.util.Optional;

public record SourceHeader(Optional<String> packageName) {
  public SourceHeader {
    packageName = java.util.Objects.requireNonNull(packageName, "packageName");
  }

  public static SourceHeader parse(SourceFile source) {
    DiagnosticBag diagnostics = new DiagnosticBag();
    Optional<String> packageName =
        new Parser(source, new Lexer(source, diagnostics).lex(), diagnostics).parsePackageHeader();
    return new SourceHeader(packageName);
  }
}
