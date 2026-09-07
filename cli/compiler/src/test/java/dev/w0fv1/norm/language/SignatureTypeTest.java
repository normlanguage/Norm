package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import org.junit.jupiter.api.Test;

final class SignatureTypeTest {
  @Test
  void preservesResolvedFunctionTypeArguments() {
    String text =
        "T identity<T>(T value) { return value } Void main() { "
            + "Function<Integer(Integer)> fn = (value) { value } "
            + "identity<Function<Integer(Integer)>>(fn) }";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:function-type"), text));
      assertFalse(analysis.hasErrors(), analysis.diagnostics().toString());
      var help = service.signatureHelp(analysis, text.lastIndexOf("(fn") + 1).orElseThrow();
      assertTrue(help.signatures().getFirst().label().contains("Function<Integer(Integer)> value"));
    }
  }

  @Test
  void parsesFunctionTypesInIncompleteCalls() {
    String text =
        "T identity<T>(T value) { return value } Void main() { "
            + "identity<Function<Integer(Integer)>>(";
    try (var service = new LanguageService()) {
      var analysis =
          service.analyze(SourceFile.of(DocumentId.of("untitled:incomplete-function"), text));
      var help = service.signatureHelp(analysis, text.length()).orElseThrow();
      assertTrue(help.signatures().getFirst().label().contains("Function<Integer(Integer)> value"));
    }
  }

  @Test
  void completesDefaultTypeArgumentsInIncompleteCalls() {
    String text =
        "U select<T, U = String>(T value, U fallback) { return fallback } "
            + "Void main() { select<Integer>(";
    try (var service = new LanguageService()) {
      var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:default-types"), text));
      var help = service.signatureHelp(analysis, text.length()).orElseThrow();
      assertTrue(help.signatures().getFirst().label().contains("String fallback"));
    }
  }
}
