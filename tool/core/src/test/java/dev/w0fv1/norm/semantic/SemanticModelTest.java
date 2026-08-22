package dev.w0fv1.norm.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import org.junit.jupiter.api.Test;

final class SemanticModelTest {
  @Test
  void bindsReferencesToExactDeclarationsAcrossScopes() {
    String text =
        "void first() { int value = 1 print(value) } "
            + "void second() { int value = 2 print(value) } "
            + "void main() {}";
    SemanticModel model = analyze(text);
    int firstUse = text.indexOf("value)");
    int secondUse = text.indexOf("value)", firstUse + 1);

    Symbol first = model.symbolAt(firstUse).orElseThrow();
    Symbol second = model.symbolAt(secondUse).orElseThrow();

    assertEquals(SymbolKind.LOCAL_VARIABLE, first.kind());
    assertEquals(SymbolKind.LOCAL_VARIABLE, second.kind());
    assertNotEquals(first.id(), second.id());
    assertEquals(text.indexOf("value"), first.declaration().orElseThrow().startOffset());
  }

  @Test
  void resolvesClassAndBuiltinMembersFromOneSymbolTable() {
    String text =
        "class Point { int x } void main() { Point point = Point(1) print(point.x) List<int> values = List<int>() values.add(1) }";
    SemanticModel model = analyze(text);
    Symbol field = model.symbolAt(text.indexOf("point.x") + "point.".length()).orElseThrow();
    Symbol method = model.symbolAt(text.indexOf("values.add") + "values.".length()).orElseThrow();

    assertEquals(SymbolKind.FIELD, field.kind());
    assertEquals("x", field.name());
    assertEquals(SymbolKind.METHOD, method.kind());
    assertEquals("add", method.name());
    assertEquals("Point", model.symbolAt(text.indexOf("Point")).orElseThrow().name());
    assertTrue(
        model.members(new SemanticType("List")).stream()
            .anyMatch(symbol -> symbol.name().equals("removeAt")));
  }

  @Test
  void keepsSemanticModelWhenAnalysisReportsErrors() {
    String text = "class Point { int x } void main() { missing(1) }";
    var analysis = new Compiler().analyze(SourceFile.of(DocumentId.of("untitled:broken"), text));

    assertTrue(analysis.hasErrors());
    assertEquals(
        "Point", analysis.semanticModel().symbolAt(text.indexOf("Point")).orElseThrow().name());
  }

  @Test
  void exposesOnlySymbolsVisibleAtTheRequestedOffset() {
    String text =
        "void main() { int outer = 1 if true { int inner = 2 print(inner) } print(outer) }";
    SemanticModel model = analyze(text);
    int innerUse = text.indexOf("inner)");
    int outerUse = text.lastIndexOf("outer)");

    assertTrue(
        model.visibleSymbols(innerUse).stream()
            .map(Symbol::name)
            .toList()
            .containsAll(java.util.List.of("outer", "inner")));
    assertTrue(
        model.visibleSymbols(outerUse).stream().noneMatch(symbol -> symbol.name().equals("inner")));
  }

  @Test
  void recordsValueAndIdentityCategories() {
    String text =
        "class Box { int value } void main() { Box box = Box(value: 1) List<int> values = List<int>() }";
    SemanticModel model = analyze(text);

    assertEquals(
        ValueCategory.IDENTITY,
        model.symbolAt(text.indexOf("box =")).orElseThrow().type().category());
    assertEquals(
        ValueCategory.VALUE,
        model.symbolAt(text.indexOf("values =")).orElseThrow().type().category());
  }

  private static SemanticModel analyze(String text) {
    return new Compiler()
        .analyze(SourceFile.of(DocumentId.of("untitled:test"), text))
        .semanticModel();
  }
}
