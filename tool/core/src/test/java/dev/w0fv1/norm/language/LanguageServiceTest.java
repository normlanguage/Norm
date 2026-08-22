package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LanguageServiceTest {
  private final LanguageService service = new LanguageService();

  @Test
  void completesMembersFromTheResolvedReceiverSymbol() {
    String text = "void main() { List<int> values = List<int>() values.add(1) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:completion"), text));
    int offset = text.indexOf("values.add") + "values.".length();

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.containsAll(List.of("add", "get", "removeAt", "size", "isEmpty")));
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
  void completesMembersThroughSubstitutedGenericFields() {
    String text =
        "class Box<T> { T value } void main() { "
            + "Box<List<int>> box = Box<List<int>>(value: List<int>()) box.value.add(1) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:generic-members"), text));
    int offset = text.indexOf("box.value.add") + "box.value.".length();

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.containsAll(List.of("add", "size", "removeAt")));
  }

  @Test
  void completesOnlyMembersOfTheCanonicalQueueType() {
    String text = "void main() { Queue<int> values = Queue<int>() values. }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:queue-members"), text));
    var completions = service.complete(analysis, text.indexOf("values.") + "values.".length());

    assertTrue(completions.stream().anyMatch(completion -> completion.label().equals("remove")));
    assertFalse(completions.stream().anyMatch(completion -> completion.label().equals("pop")));
  }

  @Test
  void hoversBuiltinTypesFromTheSharedCatalog() {
    String text = "void main() { Range values = Range(start: 0, end: 2) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:hover"), text));

    String markdown = service.hover(analysis, text.indexOf("Range")).orElseThrow().markdown();

    assertTrue(markdown.contains("end-exclusive integer range"));
  }

  @Test
  void navigatesToImportedDeclarationsAcrossFiles() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/app/Main.norm"),
            "package app import math.twice void main() { print(twice(3)) }");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/math/Numbers.norm"),
            "package math int twice(int value) { return value * 2 }");
    var analysis =
        service.analyze(
            new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));

    var definition = service.definition(analysis, entry.text().lastIndexOf("twice")).orElseThrow();

    assertEquals(library.id(), definition.document());
    assertEquals(library.text().indexOf("twice"), definition.startOffset());
  }

  @Test
  void displaysGenericSignaturesAndNavigatesTypeParameters() {
    String text =
        "class Box<T> { T value } T identity<T>(T value) { return value } "
            + "void main() { Box<int> box = Box<int>(value: identity(value: 7)) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:generics"), text));
    int classTypeParameter = text.indexOf("<T>") + 1;
    int fieldTypeParameter = text.indexOf("T value");
    int identityUse = text.lastIndexOf("identity");

    assertTrue(
        service.hover(analysis, text.indexOf("Box")).orElseThrow().markdown().contains("Box<T>"));
    assertTrue(
        service
            .hover(analysis, identityUse)
            .orElseThrow()
            .markdown()
            .contains("T identity<T>(T value)"));
    assertEquals(
        classTypeParameter,
        service.definition(analysis, fieldTypeParameter).orElseThrow().startOffset());
    assertEquals(
        2, service.rename(analysis, fieldTypeParameter, "Value").orElseThrow().locations().size());
    assertTrue(
        service.complete(analysis, text.indexOf("return value")).stream()
            .anyMatch(completion -> completion.label().equals("T")));
  }

  @Test
  void navigatesAndRenamesGenericDeclarationsAcrossFiles() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/app/Main.norm"),
            "package sample.app import sample.util.identity "
                + "void main() { print(identity(value: 3)) }");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/sample/util/Identity.norm"),
            "package sample.util public T identity<T>(T value) { return value }");
    var analysis =
        service.analyze(
            new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));
    int use = entry.text().lastIndexOf("identity");

    assertEquals(library.id(), service.definition(analysis, use).orElseThrow().document());
    assertTrue(
        service.hover(analysis, use).orElseThrow().markdown().contains("T identity<T>(T value)"));
    var rename = service.rename(analysis, use, "preserve").orElseThrow();
    assertEquals(3, rename.locations().size());
    assertTrue(
        rename.locations().stream().anyMatch(location -> location.document().equals(entry.id())));
    assertTrue(
        rename.locations().stream().anyMatch(location -> location.document().equals(library.id())));
  }

  @Test
  void treatsImportAliasesAsLocalSymbolsThatNavigateToTheirTargets() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/app/Main.norm"),
            "package sample.app import sample.util.identity as localIdentity "
                + "void main() { print(localIdentity(value: 3)) }");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/sample/util/Identity.norm"),
            "package sample.util public T identity<T>(T value) { return value }");
    var analysis =
        service.analyze(
            new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));
    int aliasDeclaration = entry.text().indexOf("localIdentity");
    int aliasUse = entry.text().lastIndexOf("localIdentity");

    assertEquals(library.id(), service.definition(analysis, aliasUse).orElseThrow().document());
    assertTrue(service.hover(analysis, aliasUse).orElseThrow().markdown().contains("identity<T>"));
    var rename = service.rename(analysis, aliasUse, "mappedIdentity").orElseThrow();
    assertEquals(2, rename.locations().size());
    assertTrue(
        rename.locations().stream().allMatch(location -> location.document().equals(entry.id())));
    assertTrue(
        rename.locations().stream()
            .anyMatch(location -> location.startOffset() == aliasDeclaration));
  }

  @Test
  void renamesImportedTypeAliasesWithoutRenamingTheirDeclarations() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/app/Main.norm"),
            "package sample.app import sample.util.Box as Cell "
                + "void main() { Cell<int> value = Cell<int>() }");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/sample/util/Box.norm"),
            "package sample.util public class Box<T> {}");
    var analysis =
        service.analyze(
            new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));
    int use = entry.text().lastIndexOf("Cell");

    assertEquals(library.id(), service.definition(analysis, use).orElseThrow().document());
    var rename = service.rename(analysis, use, "Container").orElseThrow();
    assertEquals(3, rename.locations().size());
    assertTrue(
        rename.locations().stream().allMatch(location -> location.document().equals(entry.id())));
  }

  @Test
  void keepsPrivateDeclarationsInsideTheirSourceFile() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/util/Peer.norm"),
            "package sample.util void main() { hidden() }");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/sample/util/Identity.norm"),
            "package sample.util private void hidden() {}");
    var analysis = service.analyze(new CompilationRequest(entry.id(), List.of(entry, library)));

    assertTrue(analysis.hasErrors());
    assertTrue(service.definition(analysis, entry.text().indexOf("hidden")).isEmpty());
  }

  @Test
  void navigatesToReadOnlyStandardLibraryDeclarations() {
    String text =
        "import std.math.clamp void main() { print(clamp(value: 4, minimum: 0, maximum: 2)) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:stdlib"), text));
    int use = text.lastIndexOf("clamp");

    var definition = service.definition(analysis, use).orElseThrow();

    assertEquals("stdlib", definition.document().uri().getScheme());
    assertTrue(
        service
            .standardLibrarySource(definition.document())
            .orElseThrow()
            .contains("public int clamp"));
    assertTrue(service.prepareRename(analysis, use).isEmpty());
    assertTrue(service.rename(analysis, use, "bound").isEmpty());
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
