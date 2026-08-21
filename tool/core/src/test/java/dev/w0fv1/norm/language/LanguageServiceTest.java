package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LanguageServiceTest {
  private final LanguageService service = new LanguageService();

  @Test
  void completesMembersFromTheResolvedReceiverSymbol() {
    String text = "void main() { List values = List() values.add(1) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:completion"), text));
    int offset = text.indexOf("values.add") + "values.".length();

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.containsAll(List.of("add", "get", "removeAt", "length", "isEmpty")));
    assertTrue(!labels.contains("push"));
  }

  @Test
  void completesUserMembersAndEnumMembers() {
    String text =
        "enum Color { Red, Green } class Point { int x void move(int amount) {} } "
            + "void main() { Point point = Point(1) print(point.x) print(Color.Green) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:user"), text));

    List<String> point =
        service.complete(analysis, text.indexOf("point.x") + "point.".length()).stream()
            .map(Completion::label)
            .toList();
    List<String> color =
        service.complete(analysis, text.indexOf("Color.Green") + "Color.".length()).stream()
            .map(Completion::label)
            .toList();

    assertTrue(point.containsAll(List.of("x", "move", "copy")));
    assertTrue(color.containsAll(List.of("Red", "Green")));
  }

  @Test
  void hoversBuiltinTypesFromTheSharedCatalog() {
    String text = "void main() { Range values = Range(start: 0, end: 2) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:hover"), text));

    String markdown = service.hover(analysis, text.indexOf("Range")).orElseThrow().markdown();

    assertTrue(markdown.contains("end-exclusive integer range"));
  }

  @Test
  void insertsLabelsForFunctionsWithMultipleParameters() {
    String text = "void main() { }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:call"), text));

    Completion range =
        service.complete(analysis, text.indexOf('}')).stream()
            .filter(completion -> completion.label().equals("range"))
            .findFirst()
            .orElseThrow();

    assertEquals("range(start: ${1:start}, end: ${2:end})", range.insertText());
  }

  @Test
  void findsDefinitionsAndExactReferencesBySymbolIdentity() {
    String text =
        "void first() { int value = 1 print(value) } "
            + "void second() { int value = 2 print(value) } void main() {}";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:navigation"), text));
    int firstUse = text.indexOf("value)");
    int secondDeclaration = text.indexOf("value = 2");

    var definition = service.definition(analysis, firstUse).orElseThrow();
    var references = service.references(analysis, firstUse, false);

    assertEquals(text.indexOf("value"), definition.startOffset());
    assertEquals(
        List.of(firstUse), references.stream().map(location -> location.startOffset()).toList());
    assertTrue(
        service.references(analysis, firstUse, true).stream()
            .noneMatch(location -> location.startOffset() == secondDeclaration));
  }

  @Test
  void preparesAndBuildsSemanticRenameEdits() {
    String text = "void main() { int value = 1 print(value) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:rename"), text));
    int use = text.indexOf("value)");

    var target = service.prepareRename(analysis, use).orElseThrow();
    var rename = service.rename(analysis, use, "result").orElseThrow();

    assertEquals("value", target.placeholder());
    assertEquals(use, target.location().startOffset());
    assertEquals("result", rename.newName());
    assertEquals(2, rename.locations().size());
  }

  @Test
  void rejectsBuiltinAndInvalidRenames() {
    String text = "void main() { print(1) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:rename-invalid"), text));
    int print = text.indexOf("print");

    assertTrue(service.prepareRename(analysis, print).isEmpty());
    assertTrue(service.rename(analysis, print, "write").isEmpty());
    assertThrows(IllegalArgumentException.class, () -> service.rename(analysis, print, "for"));
    assertThrows(
        IllegalArgumentException.class, () -> service.rename(analysis, print, "not-valid"));
  }

  @Test
  void rejectsRenameCollisionsInTheDeclarationScope() {
    String text = "void main() { int left = 1 int right = 2 print(left) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:rename-conflict"), text));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.rename(analysis, text.lastIndexOf("left"), "right"));
  }
}
