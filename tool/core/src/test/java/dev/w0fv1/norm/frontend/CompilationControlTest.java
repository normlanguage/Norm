package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CompilationControlTest {
  @Test
  void cancelsBeforeStartingFrontendWork() {
    CompilerSession session = new CompilerSession();
    CompilationControl control = new CompilationControl(() -> true, CompilationLimits.standard());

    assertThrows(
        CompilationCancelledException.class,
        () -> session.compile(source("cancelled", "Void main() {}"), control));
  }

  @Test
  void rejectsRequestsThatExceedDocumentAndSourceBudgets() {
    CompilerSession session = new CompilerSession();
    SourceFile first = source("first", "Void main() {}");
    SourceFile second = source("second", "Integer value() { return 1 }");
    CompilationRequest request =
        new CompilationRequest(first.id(), List.of(first, second), Set.of());

    assertThrows(
        CompilationBudgetExceededException.class,
        () ->
            session.compile(
                request,
                new CompilationControl(
                    CancellationToken.none(), new CompilationLimits(1, 1_000, 10_000, 10_000))));
    assertThrows(
        CompilationBudgetExceededException.class,
        () ->
            session.compile(
                first,
                new CompilationControl(
                    CancellationToken.none(), new CompilationLimits(2, 1, 10_000, 10_000))));
  }

  @Test
  void enforcesWorkBudgetInsideTheFrontendPipeline() {
    CompilerSession session = new CompilerSession();

    assertThrows(
        CompilationBudgetExceededException.class,
        () ->
            session.compile(
                source("work", "Void main() { printLine(1 + 2) }"),
                new CompilationControl(
                    CancellationToken.none(), new CompilationLimits(2, 1_000, 3, 10_000))));
  }

  private static SourceFile source(String name, String text) {
    return SourceFile.of(DocumentId.of("untitled:" + name), text);
  }
}
