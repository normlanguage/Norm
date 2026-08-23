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
    String text = "Void main() { List<Integer> values = List<Integer>() values.add(1) }";
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
    String text = "Void main() { List<Integer> values = List<Integer>() values.rem }";
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

    var topLevelAnalysis =
        service.analyze(SourceFile.of(DocumentId.of("untitled:declaration-template"), ""));
    Completion classCompletion =
        service.complete(topLevelAnalysis, 0).stream()
            .filter(candidate -> candidate.label().equals("class"))
            .findFirst()
            .orElseThrow();

    assertTrue(ifCompletion.snippet());
    assertEquals("if ${1:condition} {\n  ${2}\n}", ifCompletion.insertText());
    assertTrue(classCompletion.snippet());
    assertEquals("class ${1:Name} {\n  ${2}\n}", classCompletion.insertText());
  }

  @Test
  void completesUserMembersAndEnumMembers() {
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
  void completesMembersThroughSubstitutedGenericFields() {
    String text =
        "class Box<T> { T value } Void main() { "
            + "Box<List<Integer>> box = Box<List<Integer>>(value: List<Integer>()) box.value.add(1) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:generic-members"), text));
    int offset = text.indexOf("box.value.add") + "box.value.".length();

    List<String> labels =
        service.complete(analysis, offset).stream().map(Completion::label).toList();

    assertTrue(labels.containsAll(List.of("add", "size", "removeAt")));
  }

  @Test
  void completesOnlyMembersOfTheCanonicalQueueType() {
    String text = "Void main() { Queue<Integer> values = Queue<Integer>() values. }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:queue-members"), text));
    var completions = service.complete(analysis, text.indexOf("values.") + "values.".length());

    assertTrue(completions.stream().anyMatch(completion -> completion.label().equals("remove")));
    assertFalse(completions.stream().anyMatch(completion -> completion.label().equals("pop")));
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
    String text =
        "import std.math.clamp Void main() { printLine(clamp(value: 4, minimum: 0, maximum: 2)) }";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:stdlib"), text));
    int use = text.lastIndexOf("clamp");

    var definition = service.definition(analysis, use).orElseThrow();

    assertEquals("stdlib", definition.document().uri().getScheme());
    assertTrue(
        service
            .standardLibrarySource(definition.document())
            .orElseThrow()
            .contains("public Integer clamp"));
    assertTrue(service.prepareRename(analysis, use).isEmpty());
    assertTrue(service.rename(analysis, use, "bound").isEmpty());
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
    String text = "Void ping() {} Void main() { ping(";
    var analysis = service.analyze(SourceFile.of(DocumentId.of("untitled:empty-signature"), text));

    SignatureHelp help = service.signatureHelp(analysis, text.length()).orElseThrow();

    assertEquals("Void ping()", help.signatures().getFirst().label());
    assertTrue(help.signatures().getFirst().parameters().isEmpty());
    assertEquals(0, help.activeParameter());
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
