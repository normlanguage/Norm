package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.value.CompilationRequest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class IncrementalAnalysisPlanTest {
  private static IncrementalAnalysisPlan plan(
      CompilationSnapshot previous, List<ParsedDocument> current) {
    var programs = current.stream().map(ParsedDocument::syntax).toList();
    var scope = previous.semanticModel().compilationScope();
    var exports =
        current.stream()
            .map(document -> document.source().id())
            .collect(java.util.stream.Collectors.toSet());
    var declarations = new DeclarationCatalog(programs, exports, scope);
    var analyzer =
        new Analyzer(
            new SemanticAnalysisInput(
                programs,
                programs.getFirst(),
                false,
                exports,
                previous.history().nextSymbolOrdinal(),
                Set.of(),
                Set.of(),
                Set.of(),
                scope,
                declarations),
            new DiagnosticBag(),
            CompilationControl.standard().begin());
    return IncrementalAnalysisPlan.create(
        previous.history(), current, scope, analyzer.declarations());
  }

  @Test
  void rechecksChainLineEditsWithoutReanalyzingCallers() {
    String declarations =
        "class Chain { Integer finish(Integer work()) { work() + 100 } } "
            + "Chain produce(Integer work()) { work(); Chain() } "
            + "Integer finish(Integer work()) { work() + 200 } "
            + "Integer stable() { 9 } ";
    for (String separator : List.of("\n", "\r\n", "\r")) {
      String separate =
          declarations
              + "Integer run() { produce { 1 }"
              + separator
              + "finish { 2 } } Void main() { printLine(run()) }";
      String chained =
          declarations
              + "Integer run() { produce { 1 } finish { 2 } } "
              + "Void main() { printLine(run()) }";
      try (CompilerSession session = new CompilerSession()) {
        for (String text : List.of(separate, chained, separate, chained)) {
          var before = session.snapshot(SourceFile.of(Path.of("chain.norm"), text));
          assertTrue(!before.analysis().hasErrors(), () -> before.diagnostics().toString());
          var after =
              SourceParser.parse(
                  SourceFile.of(Path.of("chain.norm"), text.equals(separate) ? chained : separate));
          var plan = plan(before, List.of(after));
          assertEquals(1, plan.analyzedDeclarations());
          assertEquals(5, plan.reusedDeclarations());
        }
      }
    }
  }

  @Test
  void reusesChainDeclarationsWhenOnlyNonsemanticWhitespaceChanges() {
    String text =
        "class Chain { Integer finish(Integer work()) { work() } } "
            + "Chain produce(Integer work()) { work(); Chain() } "
            + "Integer run() { produce { 1 } finish { 2 } } Void main() { printLine(run()) }";
    try (CompilerSession session = new CompilerSession()) {
      var before = session.snapshot(SourceFile.of(Path.of("chain.norm"), text));
      assertTrue(!before.analysis().hasErrors(), () -> before.diagnostics().toString());
      var after =
          SourceParser.parse(
              SourceFile.of(
                  Path.of("chain.norm"),
                  "\r\n\r\n"
                      + text.replace("produce { 1 } finish", "produce {\r\n  1\r\n}\tfinish")));
      var plan = plan(before, List.of(after));
      assertEquals(0, plan.analyzedDeclarations());
      assertEquals(4, plan.reusedDeclarations());
    }
  }

  @Test
  void reusesDeclarationsAcrossWhitespaceOnlyEdits() {
    SourceFile first =
        SourceFile.of(
            Path.of("whitespace.norm"),
            "Integer first() { return 1 } Integer second() { return first() } Void main() {}");
    SourceFile changed =
        SourceFile.of(
            Path.of("whitespace.norm"),
            "\n  Integer first()  {  return 1  }\n\nInteger second() { return first() } Void main() {}\n");
    CompilationSnapshot previous = new CompilerSession().snapshot(first);

    IncrementalAnalysisPlan plan = plan(previous, List.of(SourceParser.parse(changed)));

    assertEquals(3, plan.declarations());
    assertEquals(3, plan.reusedDeclarations());
    assertEquals(0, plan.analyzedDeclarations());
  }

  @Test
  void analyzesOnlyAnAppendedIndependentDeclaration() {
    SourceFile first =
        SourceFile.of(Path.of("append.norm"), "Integer stable() { return 1 } Void main() {}");
    SourceFile changed =
        SourceFile.of(
            Path.of("append.norm"),
            "Integer stable() { return 1 } Void main() {} Integer added() { return 2 }");
    CompilationSnapshot previous = new CompilerSession().snapshot(first);

    IncrementalAnalysisPlan plan = plan(previous, List.of(SourceParser.parse(changed)));

    assertEquals(3, plan.declarations());
    assertEquals(2, plan.reusedDeclarations());
    assertEquals(1, plan.analyzedDeclarations());
  }

  @Test
  void reusesRemainingDeclarationsAfterAnIndependentDeclarationIsRemoved() {
    SourceFile first =
        SourceFile.of(
            Path.of("remove.norm"),
            "Integer stable() { return 1 } Integer removed() { return 2 } Void main() {}");
    SourceFile changed =
        SourceFile.of(Path.of("remove.norm"), "Integer stable() { return 1 } Void main() {}");
    CompilationSnapshot previous = new CompilerSession().snapshot(first);

    IncrementalAnalysisPlan plan = plan(previous, List.of(SourceParser.parse(changed)));

    assertEquals(2, plan.declarations());
    assertEquals(2, plan.reusedDeclarations());
    assertEquals(0, plan.analyzedDeclarations());
  }

  @Test
  void invalidatesAnOverloadFamilyAndItsCallersWhenAnOverloadIsAdded() {
    SourceFile first =
        SourceFile.of(
            Path.of("overloads.norm"),
            "Integer pick(Integer value) { return value } "
                + "Integer stable() { return 1 } Void main() { pick(1) }");
    SourceFile changed =
        SourceFile.of(
            Path.of("overloads.norm"),
            "Integer pick(Integer value) { return value } "
                + "Integer pick(String value) { return 2 } "
                + "Integer stable() { return 1 } Void main() { pick(1) }");
    CompilationSnapshot previous = new CompilerSession().snapshot(first);

    IncrementalAnalysisPlan plan = plan(previous, List.of(SourceParser.parse(changed)));

    assertEquals(4, plan.declarations());
    assertEquals(1, plan.reusedDeclarations());
    assertEquals(3, plan.analyzedDeclarations());
  }

  @Test
  void invalidatesAnOverloadFamilyAcrossDocuments() {
    SourceFile integerOverload =
        SourceFile.of(
            Path.of("src/lib/IntegerPick.norm"),
            "package lib public Integer pick(Integer value) { return value }");
    SourceFile library =
        SourceFile.of(
            Path.of("src/lib/Library.norm"), "package lib public Integer stable() { return 1 }");
    SourceFile entry =
        SourceFile.of(
            Path.of("src/app/Main.norm"), "package app import lib.pick Void main() { pick(1) }");
    CompilationRequest request =
        new CompilationRequest(
            entry.id(),
            List.of(entry, integerOverload, library),
            Set.of(integerOverload.id(), library.id()));
    CompilationSnapshot previous = new CompilerSession().snapshot(request);
    SourceFile changedLibrary =
        SourceFile.of(
            Path.of("src/lib/Library.norm"),
            "package lib public Integer stable() { return 1 } "
                + "public Integer pick(String value) { return 2 }");

    IncrementalAnalysisPlan plan =
        plan(
            previous,
            List.of(
                SourceParser.parse(entry),
                SourceParser.parse(integerOverload),
                SourceParser.parse(changedLibrary)));

    assertEquals(4, plan.declarations());
    assertEquals(1, plan.reusedDeclarations());
    assertEquals(3, plan.analyzedDeclarations());
  }

  @Test
  void reusesDeclarationsAfterTheyAreReordered() {
    SourceFile first =
        SourceFile.of(
            Path.of("reorder.norm"),
            "Integer first() { return 1 } Integer second() { return 2 } Void main() {}");
    SourceFile changed =
        SourceFile.of(
            Path.of("reorder.norm"),
            "Integer second() { return 2 } Void main() {} Integer first() { return 1 }");
    CompilationSnapshot previous = new CompilerSession().snapshot(first);

    IncrementalAnalysisPlan plan = plan(previous, List.of(SourceParser.parse(changed)));

    assertEquals(3, plan.declarations());
    assertEquals(3, plan.reusedDeclarations());
    assertEquals(0, plan.analyzedDeclarations());
  }

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
    List<ParsedDocument> current = new ArrayList<>();
    current.add(SourceParser.parse(changed));

    IncrementalAnalysisPlan plan = plan(previous, current);

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
    List<ParsedDocument> current = new ArrayList<>();
    current.add(SourceParser.parse(changed));
    current.add(SourceParser.parse(first));
    current.add(SourceParser.parse(other));

    IncrementalAnalysisPlan plan = plan(previous, current);

    assertEquals(1, plan.analyzedDeclarations());
    assertTrue(
        plan.reusable().keySet().stream()
            .noneMatch(span -> span.source().id().equals(changed.id())));
  }
}
