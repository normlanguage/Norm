package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class IncrementalAnalysisPlanTest {
  @Test
  void invalidatesChangedDeclarationsAndTheirSemanticDependents() {
    SourceFile first =
        SourceFile.of(
            Path.of("dependency.norm"),
            "Integer leaf(Integer value) { return value } Void main() { leaf(1) }");
    SourceFile changed =
        SourceFile.of(
            Path.of("dependency.norm"),
            "Integer leaf(String  value) { return 1     } Void main() { leaf(1) }");
    CompilationSnapshot previous = new CompilerSession().snapshot(first);
    List<ParsedDocument> current =
        new ArrayList<>(LanguageProfile.current().standardLibrary().documents());
    current.add(SourceParser.parse(changed, false));

    IncrementalAnalysisPlan plan = IncrementalAnalysisPlan.create(previous, current);

    assertEquals(2, plan.analyzedDeclarations());
    assertEquals(plan.declarations() - 2, plan.reusedDeclarations());
    assertTrue(
        plan.reusable().keySet().stream()
            .noneMatch(span -> span.source().id().equals(changed.id())));
  }

  @Test
  void invalidatesDeclarationsWhenTheirImportContextChanges() {
    SourceFile first =
        SourceFile.of(
            Path.of("src/first/Value.norm"), "package first public Integer value() { return 1 }");
    SourceFile other =
        SourceFile.of(
            Path.of("src/other/Value.norm"), "package other public Integer value() { return 2 }");
    SourceFile entry =
        SourceFile.of(
            Path.of("src/app/Main.norm"),
            "package app import first.value Void main() { printLine(value()) }");
    CompilationRequest request =
        new CompilationRequest(
            entry.id(), List.of(entry, first, other), Set.of(first.id(), other.id()));
    CompilationSnapshot previous = new CompilerSession().snapshot(request);
    SourceFile changed =
        SourceFile.of(
            Path.of("src/app/Main.norm"),
            "package app import other.value Void main() { printLine(value()) }");
    List<ParsedDocument> current =
        new ArrayList<>(LanguageProfile.current().standardLibrary().documents());
    current.add(SourceParser.parse(changed, false));
    current.add(SourceParser.parse(first, false));
    current.add(SourceParser.parse(other, false));

    IncrementalAnalysisPlan plan = IncrementalAnalysisPlan.create(previous, current);

    assertEquals(1, plan.analyzedDeclarations());
    assertTrue(
        plan.reusable().keySet().stream()
            .noneMatch(span -> span.source().id().equals(changed.id())));
  }
}
