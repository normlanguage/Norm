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
        "Void first() { Integer value = 1 printLine(value) } "
            + "Void second() { Integer value = 2 printLine(value) } "
            + "Void main() {}";
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
        "class Point { Integer x } Void main() { Point point = Point(1) printLine(point.x) List<Integer> values = List<>() values.add(1) }";
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
    String text = "class Point { Integer x } Void main() { missing(1) }";
    var analysis = new Compiler().analyze(SourceFile.of(DocumentId.of("untitled:broken"), text));

    assertTrue(analysis.hasErrors());
    assertEquals(
        "Point", analysis.semanticModel().symbolAt(text.indexOf("Point")).orElseThrow().name());
  }

  @Test
  void exposesOnlySymbolsVisibleAtTheRequestedOffset() {
    String text =
        "Void main() { Integer outer = 1 if true { Integer inner = 2 printLine(inner) } printLine(outer) }";
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
        "class Box { Integer value } Void main() { Box box = Box(value: 1) List<Integer> values = List<>() }";
    SemanticModel model = analyze(text);

    assertEquals(
        ValueCategory.IDENTITY,
        model.symbolAt(text.indexOf("box =")).orElseThrow().type().category());
    assertEquals(
        ValueCategory.VALUE,
        model.symbolAt(text.indexOf("values =")).orElseThrow().type().category());
  }

  @Test
  void preservesNullabilityInSemanticTypeReferences() {
    SemanticModel model =
        analyze(
            "T? identity<T>(T? value) { return value } "
                + "Void accept(List<String?>? values) {} Void main() {}");
    var identity = model.syntax().functions().getFirst();
    var accept = model.syntax().functions().get(1);

    assertEquals("T?", model.typeOf(identity.returnType()).orElseThrow().displayName());
    assertEquals(
        "T?", model.typeOf(identity.parameters().getFirst().type()).orElseThrow().displayName());
    assertEquals(
        "List<String?>?",
        model.typeOf(accept.parameters().getFirst().type()).orElseThrow().displayName());
  }

  private static SemanticModel analyze(String text) {
    return new Compiler()
        .analyze(SourceFile.of(DocumentId.of("untitled:test"), text))
        .semanticModel();
  }
}
