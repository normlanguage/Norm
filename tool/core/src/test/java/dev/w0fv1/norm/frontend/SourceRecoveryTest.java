package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

final class SourceRecoveryTest {
  private static final List<RecoveryCase> CASES =
      List.of(
          new RecoveryCase("illegal_character.norm", "NORM-LEXER-"),
          new RecoveryCase("inserted_keyword.norm", "NORM-PARSER-"),
          new RecoveryCase("missing_parenthesis.norm", "NORM-PARSER-"),
          new RecoveryCase("missing_initializer.norm", "NORM-PARSER-"),
          new RecoveryCase("damaged_signature.norm", "NORM-PARSER-"));

  @Test
  void validSourceCompiles() {
    try (CompilerSession compiler = new CompilerSession()) {
      CompilationResult result = compiler.compile(source("valid.norm"));

      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
  }

  @TestFactory
  Stream<DynamicTest> reportsDamageAndPreservesFollowingStatements() {
    return CASES.stream()
        .map(
            recoveryCase ->
                DynamicTest.dynamicTest(
                    recoveryCase.fixture(), () -> assertRecoveryCase(recoveryCase)));
  }

  private static void assertRecoveryCase(RecoveryCase recoveryCase) {
    SourceFile source = source(recoveryCase.fixture());
    ParsedDocument parsed = SourceParser.parse(source, false);

    assertSame(source, parsed.source());
    assertTrue(
        parsed.diagnostics().stream()
            .anyMatch(
                diagnostic -> diagnostic.code().value().startsWith(recoveryCase.codePrefix())),
        () -> parsed.diagnostics().toString());
    assertTrue(parsed.diagnostics().size() <= 3, () -> parsed.diagnostics().toString());
    assertTrue(
        parsed.diagnostics().stream()
            .allMatch(
                diagnostic ->
                    diagnostic.primarySpan().startOffset() >= 0
                        && diagnostic.primarySpan().endOffset() <= source.length()));
    assertTrue(
        parsed.syntax().functions().stream()
            .filter(function -> function.name().equals("main"))
            .flatMap(function -> function.body().stream())
            .anyMatch(
                statement ->
                    statement instanceof Syntax.VariableDecl variable
                        && variable.name().equals("last")));

    try (CompilerSession compiler = new CompilerSession()) {
      CompilationResult result = compiler.compile(source);

      assertFalse(result.isSuccess());
      assertTrue(result.program().isEmpty());
    }
  }

  private static SourceFile source(String fixture) {
    try (var input = SourceRecoveryTest.class.getResourceAsStream("/recovery/" + fixture)) {
      if (input == null) throw new IllegalArgumentException("missing fixture " + fixture);
      return SourceFile.of(
          Path.of(fixture), new String(input.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private record RecoveryCase(String fixture, String codePrefix) {}
}
