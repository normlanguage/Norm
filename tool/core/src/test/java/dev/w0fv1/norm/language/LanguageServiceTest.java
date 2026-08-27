package dev.w0fv1.norm.language;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.CompilationPrelude;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.frontend.LanguageProfile;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationScope;
import dev.w0fv1.norm.value.CompilationUnitId;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleGraph;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class LanguageServiceTest {
  private final LanguageService service = new LanguageService();

  @Test
  void formatsValidAuthoringSource() {
    SourceFile source =
        SourceFile.of(DocumentId.of("untitled:format"), "public main(){printLine(1)}");

    assertEquals("main() {\n  printLine(1)\n}\n", service.format(source).orElseThrow());
  }

  @Test
  void completesReferenceDeclarationsInStatementPosition() {
    String text = "Void main() { }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:ref-completion"), text));

    assertTrue(
        service.complete(analysis, text.indexOf('}')).stream()
            .anyMatch(completion -> completion.label().equals("ref")));
  }

  @Test
  void exposesIndexedLoopLocalsToHoverAndCompletion() {
    String text = "Void main() { for value,index : [10, 20] { printLine(index) ind } }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:indexed-loop"), text));

    assertEquals(
        "`Integer index`",
        service.hover(analysis, text.indexOf("index)")).orElseThrow().markdown());
    assertTrue(
        service.complete(analysis, text.lastIndexOf("ind") + 3).stream()
            .anyMatch(completion -> completion.label().equals("index")));
  }

  @Test
  void completesMembersDeclaredAfterDamagedStatements() {
    String text =
        "Void main() {\n"
            + "  printLine(return 1)\n"
            + "  String message = \"ok\"\n"
            + "  message.\n"
            + "}";
    var snapshot =
        service.snapshot(CompilationRequest.single(SourceFile.of(Path.of("damaged.norm"), text)));

    List<String> labels =
        service
            .complete(snapshot.entryDocument(), text.indexOf("message.") + "message.".length())
            .stream()
            .map(Completion::label)
            .toList();

    assertTrue(labels.contains("graphemeSize"), labels.toString());
  }

  @Test
  void completesMembersFromTheResolvedReceiverSymbol() {
    String text = "Void main() { List<Integer> values = List<>() values.add(1) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:completion"), text));
    int offset = text.indexOf("values.add") + "values.".length();

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.containsAll(List.of("add", "get", "removeAt", "size", "isEmpty")));
    assertTrue(!labels.contains("push"));
  }

  @Test
  void completesSafeAccessAndTypeLevelMembers() {
    String safeText =
        "Void main() { String? value = null Integer size = value?.codePointSize() ?? 0 }";
    var safeAnalysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:safe-completion"), safeText));
    int safeOffset = safeText.indexOf("?.codePointSize") + 2;

    List<String> safeLabels =
        service.complete(safeAnalysis, safeOffset).stream().map(Completion::label).toList();

    String typeText = "Void main() { List<Integer> values = List.filled(size: 2, value: 0) }";
    var typeAnalysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:type-member"), typeText));
    int typeOffset = typeText.indexOf("List.filled") + "List.".length();
    List<String> typeLabels =
        service.complete(typeAnalysis, typeOffset).stream().map(Completion::label).toList();

    assertTrue(safeLabels.contains("codePointSize"));
    assertTrue(typeLabels.contains("filled"));
    assertFalse(typeLabels.contains("add"));
  }

  @Test
  void includesNullInExpressionCompletionAndNullableTypesInHover() {
    String text = "Void main() { String? value = null String? result = value }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:nullable-tools"), text));

    assertTrue(
        service.complete(analysis, text.indexOf("null")).stream()
            .anyMatch(completion -> completion.label().equals("null")));
    assertTrue(
        service
            .hover(analysis, text.lastIndexOf("value"))
            .orElseThrow()
            .markdown()
            .contains("String?"));
  }

  @Test
  void completesAndReplacesAPartiallyTypedMemberName() {
    String text = "Void main() { List<Integer> values = List<>() values.rem }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:member-prefix"), text));
    int offset = text.indexOf(".rem") + ".rem".length();

    Completion completion =
        service.complete(analysis, offset).stream()
            .filter(candidate -> candidate.label().equals("removeAt"))
            .findFirst()
            .orElseThrow();

    var edit = completion.textEdit().orElseThrow();
    assertEquals("rem", text.substring(edit.location().startOffset(), edit.location().endOffset()));
    assertEquals("removeAt(${1:index})", edit.newText());
  }

  @Test
  void providesStructuredStatementAndDeclarationTemplates() {
    String statementText = "Void main() {  }";
    var statementAnalysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:statement-template"), statementText));
    Completion ifCompletion =
        service.complete(statementAnalysis, statementText.indexOf('}')).stream()
            .filter(candidate -> candidate.label().equals("if"))
            .findFirst()
            .orElseThrow();
    Completion tryCompletion =
        service.complete(statementAnalysis, statementText.indexOf('}')).stream()
            .filter(candidate -> candidate.label().equals("try"))
            .findFirst()
            .orElseThrow();
    Completion throwCompletion =
        service.complete(statementAnalysis, statementText.indexOf('}')).stream()
            .filter(candidate -> candidate.label().equals("throw"))
            .findFirst()
            .orElseThrow();

    var topLevelAnalysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:declaration-template"), ""));
    Completion classCompletion =
        service.complete(topLevelAnalysis, 0).stream()
            .filter(candidate -> candidate.label().equals("class"))
            .findFirst()
            .orElseThrow();
    Completion valueCompletion =
        service.complete(topLevelAnalysis, 0).stream()
            .filter(candidate -> candidate.label().equals("value"))
            .findFirst()
            .orElseThrow();

    assertTrue(ifCompletion.snippet());
    assertEquals("if ${1:condition} {\n  ${2}\n}", ifCompletion.insertText());
    assertTrue(tryCompletion.snippet());
    assertEquals(
        "try {\n  ${1}\n} catch ${2:Exception} ${3:error} {\n  ${4}\n}",
        tryCompletion.insertText());
    assertEquals(CompletionKind.KEYWORD, throwCompletion.kind());
    assertTrue(classCompletion.snippet());
    assertEquals("class ${1:Name} {\n  ${2}\n}", classCompletion.insertText());
    assertTrue(valueCompletion.snippet());
    assertEquals("value ${1:Name} {\n  ${2}\n}", valueCompletion.insertText());
  }

  @Test
  void completesSwitchExpressionsAndCaseBranches() {
    String statementText = "Void main() {  }";
    var statementAnalysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:switch-template"), statementText));
    Completion switched =
        service.complete(statementAnalysis, statementText.indexOf('}')).stream()
            .filter(candidate -> candidate.label().equals("switch"))
            .findFirst()
            .orElseThrow();

    String caseText =
        "enum Color { Red, Green } Void main() { Color value = Color.Red switch value {  } }";
    var caseAnalysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:case-completion"), caseText));
    int caseOffset = caseText.indexOf("switch value {  }") + "switch value { ".length();

    assertTrue(switched.snippet());
    assertTrue(switched.insertText().startsWith("switch ${1:value}"));
    assertTrue(
        service.complete(caseAnalysis, caseOffset).stream()
            .anyMatch(completion -> completion.label().equals("case")));
  }

  @Test
  void completesInterfaceDeclarationsAndConformanceTypes() {
    String topLevel = "";
    var topLevelAnalysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:interface-template"), topLevel));
    Completion declaration =
        service.complete(topLevelAnalysis, 0).stream()
            .filter(candidate -> candidate.label().equals("interface"))
            .findFirst()
            .orElseThrow();

    String conformance = "interface Named { String name() } class User implements Named {}";
    var conformanceAnalysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:interface-type"), conformance));
    int offset = conformance.indexOf("Named {}", conformance.indexOf("implements"));

    assertEquals("interface ${1:Name} {\n  ${2}\n}", declaration.insertText());
    assertTrue(
        service.complete(conformanceAnalysis, offset).stream()
            .anyMatch(
                completion ->
                    completion.label().equals("Named")
                        && completion.kind() == CompletionKind.INTERFACE));

    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/model/Named.norm"),
            "package model public interface Named { String name() }");
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/app/User.norm"), "package app class User implements Nam {}");
    var document =
        service
            .snapshot(
                new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())))
            .entryDocument();
    Completion imported =
        service.complete(document, entry.text().indexOf("Nam") + 3).stream()
            .filter(candidate -> candidate.label().equals("Named"))
            .findFirst()
            .orElseThrow();

    assertEquals(CompletionKind.INTERFACE, imported.kind());
    assertEquals(1, imported.additionalTextEdits().size());
  }

  @Test
  void completesInheritedInterfaceMembersAndBoundedTypeParameters() {
    String text =
        "interface Comparable<T> { Integer compareTo(T right) } "
            + "interface Named { String name() } "
            + "interface Ordered<T> extends Comparable<T>, Named {} "
            + "T choose<T extends Ordered<T>>(T left, T right) { left.compareTo(right: right) "
            + "return left } Void main() {}";
    var analysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:interface-members"), text));

    List<Completion> members =
        service.complete(analysis, text.indexOf("left.compareTo") + "left.".length());
    SignatureHelp help =
        service
            .signatureHelp(analysis, text.indexOf("left.compareTo(") + "left.compareTo(".length())
            .orElseThrow();

    assertTrue(members.stream().anyMatch(value -> value.label().equals("compareTo")));
    assertTrue(members.stream().anyMatch(value -> value.label().equals("name")));
    assertEquals("Integer compareTo(T right)", help.signatures().getFirst().label());
    assertTrue(
        service
            .hover(analysis, text.indexOf("choose"))
            .orElseThrow()
            .markdown()
            .contains("T choose<T extends Ordered<T>>(T left, T right)"));
    assertEquals(
        "`T extends Ordered<T>`",
        service.hover(analysis, text.indexOf("T left")).orElseThrow().markdown());
  }

  @Test
  void linksInterfaceRequirementsAndImplementationsAcrossFiles() {
    SourceFile requirement =
        SourceFile.of(
            DocumentId.of("file:///src/model/Named.norm"),
            "package model public interface Named { String name() }");
    SourceFile implementation =
        SourceFile.of(
            DocumentId.of("file:///src/model/User.norm"),
            "package model public class User implements Named { "
                + "public String name() { return \"Norm\" } }");
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/app/Main.norm"),
            "package app import model.User Void main() { User user = User() printLine(user.name()) }");
    var snapshot =
        service.snapshot(
            new CompilationRequest(
                entry.id(),
                List.of(entry, requirement, implementation),
                Set.of(requirement.id(), implementation.id())));
    var requirementAnalysis = snapshot.analysis(requirement.id());
    var implementationAnalysis = snapshot.analysis(implementation.id());
    var entryAnalysis = snapshot.analysis(entry.id());
    int requirementName = requirement.text().indexOf("name()");
    int implementationName = implementation.text().indexOf("name()");

    assertEquals(
        requirement.id(),
        service.definition(implementationAnalysis, implementationName).orElseThrow().document());
    assertEquals(
        implementation.id(),
        service
            .definition(entryAnalysis, entry.text().lastIndexOf("name"))
            .orElseThrow()
            .document());
    assertTrue(
        service.references(requirementAnalysis, requirementName, false).stream()
            .anyMatch(
                location ->
                    location.document().equals(implementation.id())
                        && location.startOffset() == implementationName));
    RenameEdit rename =
        service.rename(requirementAnalysis, requirementName, "displayName").orElseThrow();
    assertTrue(
        rename.locations().stream().anyMatch(value -> value.document().equals(requirement.id())));
    assertTrue(
        rename.locations().stream()
            .anyMatch(value -> value.document().equals(implementation.id())));
    assertTrue(rename.locations().stream().anyMatch(value -> value.document().equals(entry.id())));
  }

  @Test
  void completesUserMembersAndEnumVariants() {
    String text =
        "enum Color { Red, Green } class Point { Integer x Void move(Integer amount) {} } "
            + "Void main() { Point point = Point(1) printLine(point.x) printLine(Color.Green) }";
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
  void completesValueMembersWithoutIdentityCopy() {
    String text =
        "value Point { Integer x Integer twice() { return x * 2 } } "
            + "Void main() { Point point = Point(x: 1) printLine(point.x) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:value"), text));

    List<String> members =
        service.complete(analysis, text.indexOf("point.x") + "point.".length()).stream()
            .map(Completion::label)
            .toList();

    assertTrue(members.containsAll(List.of("x", "twice")));
    assertFalse(members.contains("copy"));
  }

  @Test
  void completesInheritedMembersAndHidesOverriddenMethods() {
    String text =
        "class Base<T> { public T value public T read() { return value } } "
            + "class Child extends Base<String> { Child(String initial) { super(value: initial) } "
            + "public String read() { return value } } "
            + "Void main() { Child child = Child(initial: \"Norm\") printLine(child.value) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:inheritance"), text));

    List<String> members =
        service.complete(analysis, text.indexOf("child.value") + "child.".length()).stream()
            .map(Completion::label)
            .toList();

    assertTrue(members.containsAll(List.of("value", "read", "copy")));
    assertEquals(1, members.stream().filter("read"::equals).count());
  }

  @Test
  void completesAppliedGenericEnumVariantsWithPayloadSnippets() {
    String text =
        "enum Result<T, E> { Ok(T value), Err(E error) } Void main() { "
            + "Result<Integer, String> result = Result<Integer, String>. }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:adt-completion"), text));
    int offset = text.indexOf("Result<Integer, String>. }") + "Result<Integer, String>.".length();

    Completion ok =
        service.complete(analysis, offset).stream()
            .filter(candidate -> candidate.label().equals("Ok"))
            .findFirst()
            .orElseThrow();
    Completion err =
        service.complete(analysis, offset).stream()
            .filter(candidate -> candidate.label().equals("Err"))
            .findFirst()
            .orElseThrow();

    assertEquals("Ok(value: ${1:value})", ok.insertText());
    assertEquals("Result<Integer, String> Ok(Integer value)", ok.detail());
    assertEquals("Err(error: ${1:error})", err.insertText());
    assertEquals("Result<Integer, String> Err(String error)", err.detail());
  }

  @Test
  void completesMembersThroughSubstitutedGenericFields() {
    String text =
        "class Box<T> { T value } Void main() { "
            + "Box<List<Integer>> box = Box<>(value: List<>()) box.value.add(1) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:generic-members"), text));
    int offset = text.indexOf("box.value.add") + "box.value.".length();

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.containsAll(List.of("add", "size", "removeAt")));
  }

  @Test
  void completesMembersThroughImportedSubstitutedGenericFieldsInIncompleteCode() {
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/sample/util/Box.norm"),
            "package sample.util public class Box<T> { T value }");
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/app/Main.norm"),
            "package sample.app import sample.util.Box Void main() { "
                + "Box<List<Integer>> box = Box<>(value: List<>()) box.value. }");
    var document =
        service
            .snapshot(
                new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())))
            .entryDocument();

    List<String> labels =
        service
            .complete(document, entry.text().indexOf("box.value.") + "box.value.".length())
            .stream()
            .map(Completion::label)
            .toList();

    assertTrue(labels.containsAll(List.of("add", "size", "removeAt")), labels.toString());
  }

  @Test
  void completesOnlyMembersOfTheCanonicalQueueType() {
    String text = "Void main() { Queue<Integer> values = Queue<>() values. }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:queue-members"), text));
    var completions = service.complete(analysis, text.indexOf("values.") + "values.".length());

    assertTrue(completions.stream().anyMatch(completion -> completion.label().equals("remove")));
    assertFalse(completions.stream().anyMatch(completion -> completion.label().equals("pop")));
  }

  @Test
  void exposesInferredLocalTypesToHoverAndCompletion() {
    String text = "Void main() { var values = List<Integer>() values. }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:inferred-local"), text));
    int use = text.indexOf("values.");

    assertEquals("`List<Integer> values`", service.hover(analysis, use).orElseThrow().markdown());
    assertTrue(
        service.complete(analysis, use + "values.".length()).stream()
            .anyMatch(completion -> completion.label().equals("add")));
  }

  @Test
  void hoversBuiltinTypesFromTheSharedCatalog() {
    String text = "Void main() { Range values = Range(start: 0, end: 2) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:hover"), text));

    String markdown = service.hover(analysis, text.indexOf("Range")).orElseThrow().markdown();

    assertTrue(markdown.contains("end-exclusive integer range"));
  }

  @Test
  void navigatesToImportedDeclarationsAcrossFiles() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/app/Main.norm"),
            "package app import math.twice Void main() { printLine(twice(3)) }");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/math/Numbers.norm"),
            "package math Integer twice(Integer value) { return value * 2 }");
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
            + "Void main() { Box<Integer> box = Box<Integer>(value: identity(value: 7)) }";
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
                + "Void main() { printLine(identity(value: 3)) }");
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
                + "Void main() { printLine(localIdentity(value: 3)) }");
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
                + "Void main() { Cell<Integer> value = Cell<Integer>() }");
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
            "package sample.util Void main() { hidden() }");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/sample/util/Identity.norm"),
            "package sample.util private Void hidden() {}");
    var analysis = service.analyze(new CompilationRequest(entry.id(), List.of(entry, library)));

    assertTrue(analysis.hasErrors());
    assertTrue(service.definition(analysis, entry.text().indexOf("hidden")).isEmpty());
  }

  @Test
  void navigatesToReadOnlyStandardLibraryDeclarations() {
    SourceFile library =
        SourceFile.of(
            DocumentId.of("stdlib:/std/math/integer.norm"),
            "package std.math Integer clamp(Integer value, Integer minimum, Integer maximum) "
                + "{ return value }");
    CompilationPrelude prelude =
        new CompilationPrelude(
            List.of(library),
            Set.of(library.id()),
            CompilationScope.module(
                new ModuleCoordinate("std", 1), Map.of(library.id(), "std/math/integer.norm")));
    String text =
        "import std.math.clamp Void main() { printLine(clamp(value: 4, minimum: 0, maximum: 2)) }";
    try (LanguageService preludeService =
        new LanguageService(new CompilerSession(LanguageProfile.withPrelude(prelude)))) {
      var analysis = preludeService.analyze(SourceFile.of(DocumentId.of("untitled:stdlib"), text));
      int use = text.lastIndexOf("clamp");

      var definition = preludeService.definition(analysis, use).orElseThrow();

      assertEquals("stdlib", definition.document().uri().getScheme());
      assertTrue(
          preludeService
              .standardLibrarySource(definition.document())
              .orElseThrow()
              .contains("Integer clamp"));
      assertTrue(preludeService.prepareRename(analysis, use).isEmpty());
      assertTrue(preludeService.rename(analysis, use, "bound").isEmpty());
    }
  }

  @Test
  void insertsLabelsForFunctionsWithMultipleParameters() {
    String text = "Void main() { }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:call"), text));

    Completion range =
        service.complete(analysis, text.indexOf('}')).stream()
            .filter(completion -> completion.label().equals("range"))
            .findFirst()
            .orElseThrow();

    assertEquals("range(start: ${1:start}, end: ${2:end})", range.insertText());
  }

  @Test
  void completesBoundMethodReferencesWithoutCallParentheses() {
    String text =
        "class Counter { public Integer add(Integer amount) { return amount } } "
            + "Void main() { Counter counter = Counter() Function<Integer(Integer)> add = counter:: }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:method-reference"), text));
    int offset = text.indexOf("counter::") + "counter::".length();

    Completion completion =
        service.complete(analysis, offset).stream()
            .filter(value -> value.label().equals("add"))
            .findFirst()
            .orElseThrow();

    assertEquals("add", completion.insertText());
    assertFalse(completion.snippet());
  }

  @Test
  void providesSignatureHelpForFunctionValues() {
    String text =
        "Void main() { Function<Integer(Integer)> transform = (value) { value * 2 } transform( }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:function-value"), text));

    SignatureHelp help = service.signatureHelp(analysis, text.length()).orElseThrow();

    assertEquals("Integer transform(Integer argument0)", help.signatures().getFirst().label());
  }

  @Test
  void completesFunctionValuesAsInvocations() {
    String text =
        "Void main() { Function<Integer(Integer)> transform = (value) { value * 2 } trans }";
    var analysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:function-completion"), text));
    int offset = text.lastIndexOf("trans") + "trans".length();

    Completion completion =
        service.complete(analysis, offset).stream()
            .filter(value -> value.label().equals("transform"))
            .findFirst()
            .orElseThrow();

    assertEquals(CompletionKind.FUNCTION, completion.kind());
    assertEquals("transform(${1:argument0})", completion.insertText());
  }

  @Test
  void ranksLambdaResultCandidatesByTheExpectedReturnType() {
    String text =
        "Void main() { String label = \"ready\" Integer count = 1 "
            + "Function<String(Integer)> choose = (value) { label } }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:lambda-result"), text));
    int offset = text.lastIndexOf("label");

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.indexOf("label") < labels.indexOf("count"));
  }

  @Test
  void ranksVariableInitializerCandidatesByExpectedType() {
    String text =
        "Void main() { String label = \"ready\" Integer count = 1 String result = label }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:initializer"), text));

    List<String> labels =
        service.complete(analysis, text.lastIndexOf("label")).stream()
            .map(Completion::label)
            .toList();

    assertTrue(labels.indexOf("label") < labels.indexOf("count"));
  }

  @Test
  void ranksReturnCandidatesByTheCallableReturnType() {
    String text =
        "String choose() { String label = \"ready\" Integer count = 1 return label } "
            + "Void main() { printLine(choose()) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:return"), text));

    List<String> labels =
        service.complete(analysis, text.indexOf("return label") + "return ".length()).stream()
            .map(Completion::label)
            .toList();

    assertTrue(labels.indexOf("label") < labels.indexOf("count"));
  }

  @Test
  void ranksArgumentCandidatesByTheActiveParameterType() {
    String text =
        "Void consume(String value) {} Void main() { "
            + "String label = \"ready\" Integer count = 1 consume(label) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:argument"), text));

    List<String> labels =
        service.complete(analysis, text.lastIndexOf("label")).stream()
            .map(Completion::label)
            .toList();

    assertTrue(labels.indexOf("label") < labels.indexOf("count"));
  }

  @Test
  void completesExpectedValuesInAnIncompleteReturnStatement() {
    String text = "String choose() { String label = \"ready\" Integer count = 1 return  }";
    var analysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:incomplete-return"), text));
    int offset = text.indexOf("return") + "return ".length();

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.containsAll(List.of("label", "count")));
    assertTrue(labels.indexOf("label") < labels.indexOf("count"));
  }

  @Test
  void completesExpectedValuesInAnIncompleteVariableInitializer() {
    String text = "Void main() { String label = \"ready\" Integer count = 1 String result =  }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:incomplete-value"), text));
    int offset = text.indexOf("=  }") + 2;

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.containsAll(List.of("label", "count")));
    assertTrue(labels.indexOf("label") < labels.indexOf("count"));
  }

  @Test
  void completesExpectedValuesInAnIncompleteCallArgument() {
    String text =
        "Void consume(String value) {} Void main() { "
            + "String label = \"ready\" Integer count = 1 consume( }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:incomplete-call"), text));
    int offset = text.lastIndexOf('(') + 1;

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.containsAll(List.of("label", "count")));
    assertTrue(labels.indexOf("label") < labels.indexOf("count"));
  }

  @Test
  void providesSignatureHelpForAnIncompleteCall() {
    String text = "Void consume(String value, Integer count) {} Void main() { consume(";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:signature"), text));

    SignatureHelp help = service.signatureHelp(analysis, text.length()).orElseThrow();

    assertEquals("Void consume(String value, Integer count)", help.signatures().getFirst().label());
    assertEquals(0, help.activeParameter());
  }

  @Test
  void providesSignatureHelpForAppliedGenericEnumVariantConstructors() {
    String text =
        "enum Result<T, E> { Ok(T value), Err(E error) } Void main() { "
            + "Result<Integer, String>.Ok(";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:adt-signature"), text));

    SignatureHelp help = service.signatureHelp(analysis, text.length()).orElseThrow();

    assertEquals("Result<Integer, String> Ok(Integer value)", help.signatures().getFirst().label());
    assertEquals("Integer value", help.signatures().getFirst().parameters().getFirst().label());
  }

  @Test
  void carriesExpectedTypesIntoSwitchBreakValues() {
    String text =
        "enum Result<T, E> { Ok(T value), Err(E error) } "
            + "String describe(Result<Integer, String> result) { "
            + "String label = \"ready\" Integer count = 1 return switch result { "
            + "case Ok(Integer value) { break  } case Err(String error) { break error } } }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:switch-expected"), text));
    int offset = text.indexOf("break  }") + "break ".length();

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.indexOf("label") < labels.indexOf("count"));
  }

  @Test
  void providesAllVisibleOverloadSignatures() {
    String text =
        "Integer choose(Integer value) { return value } "
            + "String choose(String value) { return value } Void main() { choose(";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:overloads"), text));

    SignatureHelp help = service.signatureHelp(analysis, text.length()).orElseThrow();
    List<String> signatures = help.signatures().stream().map(SignatureInformation::label).toList();

    assertTrue(signatures.contains("Integer choose(Integer value)"));
    assertTrue(signatures.contains("String choose(String value)"));
  }

  @Test
  void providesSignatureHelpForAZeroParameterCall() {
    String text = "ping() {} main() { ping(";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:empty-signature"), text));

    SignatureHelp help = service.signatureHelp(analysis, text.length()).orElseThrow();

    assertEquals("Void ping()", help.signatures().getFirst().label());
    assertTrue(help.signatures().getFirst().parameters().isEmpty());
    assertEquals(0, help.activeParameter());
  }

  @Test
  void exposesTheSpecializedReceiverTypeOfFluentMethods() {
    String text =
        "class Box<T> { T value set(T next) { value = next } } "
            + "main() { Box<String> box = Box<String>(value: \"first\") box.set(\"next\"). }";
    var analysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:fluent-completion"), text));
    int call = text.indexOf("box.set(") + "box.set(".length();
    int member = text.lastIndexOf('.') + 1;

    SignatureHelp help = service.signatureHelp(analysis, call).orElseThrow();
    List<String> completions =
        service.complete(analysis, member).stream().map(Completion::label).toList();

    assertEquals("Box<String> set(String next)", help.signatures().getFirst().label());
    assertTrue(completions.containsAll(List.of("set", "value")));
  }

  @Test
  void tracksTheActiveNamedParameter() {
    String text =
        "Void consume(String value, Integer count) {} Void main() { "
            + "consume(count: 1, value: \"ready\") }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:named-signature"), text));
    int offset = text.indexOf("\"ready\"");

    SignatureHelp help = service.signatureHelp(analysis, offset).orElseThrow();

    assertEquals(0, help.activeParameter());
    assertEquals("String value", help.signatures().getFirst().parameters().getFirst().label());
  }

  @Test
  void substitutesExplicitGenericArgumentsInCompletionAndSignatureHelp() {
    String text =
        "Void accept<T>(T value) {} Void main() { "
            + "Integer number = 1 String label = \"ready\" accept<Integer>( }";
    var analysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:generic-call-site"), text));
    int offset = text.lastIndexOf('(') + 1;

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();
    SignatureHelp help = service.signatureHelp(analysis, offset).orElseThrow();

    assertTrue(labels.indexOf("number") < labels.indexOf("label"));
    assertEquals("Void accept<Integer>(Integer value)", help.signatures().getFirst().label());
  }

  @Test
  void specializesOwnerAndMethodTypeArgumentsForIncompleteMethodCalls() {
    String text =
        "class Box<T> { Void set(T value) {} U convert<U>(T source, U fallback) { "
            + "return fallback } } Void main() { Box<String> box = Box<String>() "
            + "box.set( box.convert<Integer>(";
    var analysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:source-method-signature"), text));

    SignatureHelp set =
        service
            .signatureHelp(analysis, text.indexOf("box.set(") + "box.set(".length())
            .orElseThrow();
    SignatureHelp convert = service.signatureHelp(analysis, text.length()).orElseThrow();

    assertEquals("Void set(String value)", set.signatures().getFirst().label());
    assertEquals(
        "Integer convert<Integer>(String source, Integer fallback)",
        convert.signatures().getFirst().label());
  }

  @Test
  void usesResolvedCallInstantiationForCompleteGenericMethodCalls() {
    String text =
        "class Box<T> { Void set(T next) {} U convert<U>(U fallback) { return fallback } } "
            + "Void main() { Box<String> box = Box<String>() String label = \"Norm\" "
            + "box.set(label) Integer result = box.convert(fallback: 7) }";
    var analysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:resolved-call-signature"), text));

    SignatureHelp set =
        service.signatureHelp(analysis, text.indexOf("label) Integer")).orElseThrow();
    SignatureHelp convert = service.signatureHelp(analysis, text.indexOf("7) }")).orElseThrow();

    assertEquals("Void set(String next)", set.signatures().getFirst().label());
    assertEquals(
        "Integer convert<Integer>(Integer fallback)", convert.signatures().getFirst().label());
  }

  @Test
  void providesEveryApplicableImportedAliasOverload() {
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/util/Choose.norm"),
            "package util public String choose(String value) { return value } "
                + "public T choose<T>(T value) { return value }");
    SourceFile plain =
        SourceFile.of(
            DocumentId.of("file:///src/app/Plain.norm"),
            "package app import util.choose as select Void main() { select(");
    SourceFile explicit =
        SourceFile.of(
            DocumentId.of("file:///src/app/Explicit.norm"),
            "package app import util.choose as select Void main() { select<Integer>(");

    SignatureHelp plainHelp =
        service
            .signatureHelp(
                service
                    .snapshot(
                        new CompilationRequest(
                            plain.id(), List.of(plain, library), Set.of(library.id())))
                    .entryDocument(),
                plain.text().length())
            .orElseThrow();
    SignatureHelp explicitHelp =
        service
            .signatureHelp(
                service
                    .snapshot(
                        new CompilationRequest(
                            explicit.id(), List.of(explicit, library), Set.of(library.id())))
                    .entryDocument(),
                explicit.text().length())
            .orElseThrow();

    assertEquals(
        Set.of("String select(String value)", "T select<T>(T value)"),
        plainHelp.signatures().stream()
            .map(SignatureInformation::label)
            .collect(java.util.stream.Collectors.toSet()));
    assertEquals(1, explicitHelp.signatures().size());
    assertEquals(
        "Integer select<Integer>(Integer value)", explicitHelp.signatures().getFirst().label());
  }

  @Test
  void resolvesSourceMembersByCanonicalTypeIdentity() {
    SourceFile firstLibrary =
        SourceFile.of(
            DocumentId.of("file:///src/first/Box.norm"),
            "package first public class Box<T> { public Void fromFirst(T value) {} }");
    SourceFile secondLibrary =
        SourceFile.of(
            DocumentId.of("file:///src/second/Box.norm"),
            "package second public class Box { public Void fromSecond() {} }");
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/app/Main.norm"),
            "package app import first.Box as First import second.Box as Second "
                + "Void main() { First<String> value = First<String>() value. }");
    var document =
        service
            .snapshot(
                new CompilationRequest(
                    entry.id(),
                    List.of(entry, firstLibrary, secondLibrary),
                    Set.of(firstLibrary.id(), secondLibrary.id())))
            .entryDocument();

    List<String> labels =
        service.complete(document, entry.text().lastIndexOf('.') + 1).stream()
            .map(Completion::label)
            .toList();

    assertTrue(labels.contains("fromFirst"));
    assertFalse(labels.contains("fromSecond"));
    Completion member =
        service.complete(document, entry.text().lastIndexOf('.') + 1).stream()
            .filter(completion -> completion.label().equals("fromFirst"))
            .findFirst()
            .orElseThrow();
    assertEquals("Void fromFirst(String value)", member.detail());
  }

  @Test
  void completesAndDescribesGenericConstructorsFromClassFields() {
    String text =
        "class Box<T> { T value } Void main() { "
            + "Integer number = 1 String label = \"ready\" Box<Integer> box = Box<Integer>( }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:constructor-site"), text));
    int argumentOffset = text.lastIndexOf('(') + 1;

    List<String> labels =
        service.complete(analysis, argumentOffset).stream().map(Completion::label).toList();
    SignatureHelp help = service.signatureHelp(analysis, argumentOffset).orElseThrow();

    assertTrue(labels.indexOf("number") < labels.indexOf("label"));
    assertEquals("Box<Integer>(Integer value)", help.signatures().getFirst().label());
  }

  @Test
  void insertsAConstructorSnippetMatchingTheExpectedGenericType() {
    String text = "class Box<T> { T value } Void main() { Box<Integer> box = B }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:constructor"), text));

    Completion box =
        service.complete(analysis, text.lastIndexOf('B') + 1).stream()
            .filter(completion -> completion.label().equals("Box"))
            .findFirst()
            .orElseThrow();

    assertEquals("Box<Integer>(value: ${1:value})", box.insertText());
  }

  @Test
  void completesExportedProjectSymbolsWithAnImportEdit() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/app/Main.norm"),
            "package sample.app\r\n\r\nVoid main() { twi }\r\n");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/sample/math/Numbers.norm"),
            "package sample.math\n\npublic Integer twice(Integer value) { return value * 2 }\n");
    var snapshot =
        service.snapshot(
            new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));

    Completion twice =
        service.complete(snapshot.entryDocument(), entry.text().indexOf("twi") + 3).stream()
            .filter(completion -> completion.label().equals("twice"))
            .findFirst()
            .orElseThrow();

    assertEquals("twice(${1:value})", twice.insertText());
    assertEquals(1, twice.additionalTextEdits().size());
    assertEquals(
        "\r\n\r\nimport sample.math.twice", twice.additionalTextEdits().getFirst().newText());
  }

  @Test
  void completesSymbolsInTheSelectedProjectDocument() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/app/Main.norm"),
            "package sample.app\n\nVoid main() {}\n");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/sample/math/Numbers.norm"),
            "package sample.math\n\npublic Integer twice(Integer value) { Integer local = value return local }\n");
    var snapshot =
        service.snapshot(
            new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));
    int offset = library.text().lastIndexOf("local");

    List<String> labels =
        service.complete(snapshot.document(library.id()).orElseThrow(), offset).stream()
            .map(Completion::label)
            .toList();

    assertTrue(labels.contains("local"));
  }

  @Test
  void excludesPrivateAndUnexportedSymbolsFromImportCompletion() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/app/Main.norm"),
            "package sample.app\n\nVoid main() { }\n");
    SourceFile exported =
        SourceFile.of(
            DocumentId.of("file:///src/sample/api/Public.norm"),
            "package sample.api public Integer visible() { return 1 } "
                + "private Integer hidden() { return 2 }");
    SourceFile internal =
        SourceFile.of(
            DocumentId.of("file:///src/sample/internal/Internal.norm"),
            "package sample.internal public Integer internal() { return 3 }");
    var snapshot =
        service.snapshot(
            new CompilationRequest(
                entry.id(), List.of(entry, exported, internal), Set.of(exported.id())));

    List<String> labels =
        service.complete(snapshot.entryDocument(), entry.text().indexOf('}')).stream()
            .map(Completion::label)
            .toList();

    assertTrue(labels.contains("visible"));
    assertFalse(labels.contains("hidden"));
    assertFalse(labels.contains("internal"));
  }

  @Test
  void excludesSymbolsFromTransitiveModuleDependencies() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/application/Main.norm"),
            "package application Void main() { }");
    SourceFile direct =
        SourceFile.of(
            DocumentId.of("file:///src/middle/Value.norm"),
            "package middle public Integer directValue() { return 1 }");
    SourceFile transitive =
        SourceFile.of(
            DocumentId.of("file:///src/base/Value.norm"),
            "package base public Integer transitiveValue() { return 2 }");
    ModuleCoordinate applicationModule = new ModuleCoordinate("application", 1);
    ModuleCoordinate middleModule = new ModuleCoordinate("middle", 1);
    ModuleCoordinate baseModule = new ModuleCoordinate("base", 1);
    CompilationScope scope =
        new CompilationScope(
            Map.of(
                entry.id(), new ModuleSourceCoordinate(applicationModule, "application/Main.norm"),
                direct.id(), new ModuleSourceCoordinate(middleModule, "middle/Value.norm"),
                transitive.id(), new ModuleSourceCoordinate(baseModule, "base/Value.norm")),
            new ModuleGraph(
                Map.of(
                    applicationModule, Set.of(middleModule),
                    middleModule, Set.of(baseModule),
                    baseModule, Set.of())));
    var snapshot =
        service.snapshot(
            new CompilationRequest(
                new CompilationUnitId(entry.id().uri()),
                scope,
                entry.id(),
                List.of(entry, direct, transitive),
                Set.of(direct.id(), transitive.id())));

    List<String> labels =
        service.complete(snapshot.entryDocument(), entry.text().indexOf('}')).stream()
            .map(Completion::label)
            .toList();

    assertTrue(labels.contains("directValue"));
    assertFalse(labels.contains("transitiveValue"));
  }

  @Test
  void preservesAmbiguousAutoImportCandidatesWithTheirQualifiedNames() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/app/Main.norm"),
            "package sample.app\n\nVoid main() { twi }\n");
    SourceFile first =
        SourceFile.of(
            DocumentId.of("file:///src/sample/first/Numbers.norm"),
            "package sample.first public Integer twice(Integer value) { return value * 2 }");
    SourceFile second =
        SourceFile.of(
            DocumentId.of("file:///src/sample/second/Numbers.norm"),
            "package sample.second public Integer twice(Integer value) { return value + value }");
    var snapshot =
        service.snapshot(
            new CompilationRequest(
                entry.id(), List.of(entry, first, second), Set.of(first.id(), second.id())));

    List<Completion> candidates =
        service.complete(snapshot.entryDocument(), entry.text().indexOf("twi") + 3).stream()
            .filter(completion -> completion.label().equals("twice"))
            .toList();

    assertEquals(2, candidates.size());
    assertTrue(
        candidates.stream().anyMatch(value -> value.detail().contains("sample.first.twice")));
    assertTrue(
        candidates.stream().anyMatch(value -> value.detail().contains("sample.second.twice")));
  }

  @Test
  void completesQualifiedNamesInsideImportDeclarations() {
    SourceFile entry =
        SourceFile.of(
            DocumentId.of("file:///src/sample/app/Main.norm"),
            "package sample.app\n\nimport sample.ma\n\nVoid main() {}\n");
    SourceFile library =
        SourceFile.of(
            DocumentId.of("file:///src/sample/math/Numbers.norm"),
            "package sample.math public Integer twice(Integer value) { return value * 2 }");
    var snapshot =
        service.snapshot(
            new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));
    int offset = entry.text().indexOf("sample.ma") + "sample.ma".length();

    Completion completion =
        service.complete(snapshot.entryDocument(), offset).stream()
            .filter(candidate -> candidate.label().equals("sample.math.twice"))
            .findFirst()
            .orElseThrow();

    assertEquals("sample.math.twice", completion.insertText());
    assertEquals(
        "sample.ma",
        entry
            .text()
            .substring(
                completion.textEdit().orElseThrow().location().startOffset(),
                completion.textEdit().orElseThrow().location().endOffset()));
  }

  @Test
  void findsDefinitionsAndExactReferencesBySymbolIdentity() {
    String text =
        "Void first() { Integer value = 1 printLine(value) } "
            + "Void second() { Integer value = 2 printLine(value) } Void main() {}";
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
    String text = "Void main() { Integer value = 1 printLine(value) }";
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
    String text = "Void main() { printLine(1) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:rename-invalid"), text));
    int printLine = text.indexOf("printLine");

    assertTrue(service.prepareRename(analysis, printLine).isEmpty());
    assertTrue(service.rename(analysis, printLine, "write").isEmpty());
    assertThrows(IllegalArgumentException.class, () -> service.rename(analysis, printLine, "for"));
    assertThrows(
        IllegalArgumentException.class, () -> service.rename(analysis, printLine, "not-valid"));
  }

  @Test
  void rejectsRenameCollisionsInTheDeclarationScope() {
    String text = "Void main() { Integer left = 1 Integer right = 2 printLine(left) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:rename-conflict"), text));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.rename(analysis, text.lastIndexOf("left"), "right"));
  }
}
