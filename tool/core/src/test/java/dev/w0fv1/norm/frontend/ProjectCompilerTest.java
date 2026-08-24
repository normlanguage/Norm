package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ProgramRunner;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProjectCompilerTest {
  @Test
  void resolvesExplicitImportsAcrossPackages() {
    SourceFile entry =
        source(
            "src/app/Main.norm",
            "package app import math.twice Void main() { printLine(twice(4)) }");
    SourceFile library =
        source(
            "src/math/Numbers.norm",
            "package math public Integer twice(Integer value) { return value * 2 }");

    CompilationResult result =
        new Compiler()
            .compile(
                new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void resolvesMutualCallsWithoutFileOrdering() {
    SourceFile entry =
        source(
            "src/check/Main.norm",
            "package check Void main() { printLine(even(8)) } "
                + "Boolean odd(Integer value) { if value == 0 { return false } return even(value - 1) }");
    SourceFile second =
        source(
            "src/check/Even.norm",
            "package check Boolean even(Integer value) { if value == 0 { return true } return odd(value - 1) }");

    CompilationResult result =
        new Compiler().compile(new CompilationRequest(entry.id(), List.of(second, entry)));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void executesImportedFunctionsFromTheirOwnSourceFile() {
    SourceFile entry =
        source(
            "src/app/Main.norm",
            "package app import math.twice Void main() { printLine(twice(6)) }");
    SourceFile library =
        source(
            "src/math/Numbers.norm",
            "package math public Integer twice(Integer value) { return value * 2 }");
    CompilationResult result =
        new Compiler()
            .compile(
                new CompilationRequest(entry.id(), List.of(library, entry), Set.of(library.id())));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    StringWriter output = new StringWriter();
    new ProgramRunner().run(result.program().orElseThrow(), new PrintWriter(output));
    assertTrue(output.toString().equals("12" + System.lineSeparator()));
  }

  @Test
  void executesAliasedImports() {
    SourceFile entry =
        source(
            "src/app/Main.norm",
            "package app import math.twice as double Void main() { printLine(double(5)) }");
    SourceFile library =
        source(
            "src/math/Numbers.norm",
            "package math Integer twice(Integer value) { return value * 2 }");
    CompilationResult result =
        new Compiler()
            .compile(
                new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    StringWriter output = new StringWriter();
    new ProgramRunner().run(result.program().orElseThrow(), new PrintWriter(output));
    assertTrue(output.toString().equals("10" + System.lineSeparator()));
  }

  @Test
  void executesTheResolvedOverloadThroughAnImportedAlias() {
    SourceFile entry =
        source(
            "src/app/Main.norm",
            "package app import util.choose as select "
                + "Void main() { printLine(select<Integer>(value: 7)) }");
    SourceFile library =
        source(
            "src/util/Choose.norm",
            "package util public String choose(String value) { return \"plain\" } "
                + "public T choose<T>(T value) { return value }");
    CompilationRequest request =
        new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id()));
    Compiler compiler = new Compiler();
    var analysis = compiler.analyze(request);
    var model = analysis.semanticModel();
    var generic =
        model.symbols().stream()
            .filter(symbol -> symbol.name().equals("choose"))
            .filter(
                symbol ->
                    symbol.typeParameters().stream()
                        .map(dev.w0fv1.norm.semantic.TypeParameterInfo::name)
                        .toList()
                        .equals(List.of("T")))
            .findFirst()
            .orElseThrow();
    int callOffset = entry.text().indexOf("select<Integer>");
    var alias = model.symbolAt(entry.id(), callOffset).orElseThrow();

    assertTrue(
        model.references(generic.id()).stream()
            .anyMatch(
                span -> span.source().id().equals(entry.id()) && span.startOffset() == callOffset));
    assertTrue(model.isAlias(alias.id()));
    assertTrue(
        model.authoringReferences(alias.id()).stream()
            .anyMatch(span -> span.startOffset() == callOffset));

    CompilationResult result = compiler.compile(request);

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    StringWriter output = new StringWriter();
    new ProgramRunner().run(result.program().orElseThrow(), new PrintWriter(output));
    assertTrue(output.toString().equals("7" + System.lineSeparator()));
  }

  @Test
  void infersImportedGenericFunctions() {
    SourceFile entry =
        source(
            "src/app/Main.norm",
            "package app import util.identity Void main() { String value = identity(\"ok\") printLine(value) }");
    SourceFile library =
        source("src/util/Identity.norm", "package util T identity<T>(T value) { return value }");
    CompilationResult result =
        new Compiler()
            .compile(
                new CompilationRequest(entry.id(), List.of(library, entry), Set.of(library.id())));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void keepsSameNamedDeclarationsInDifferentPackagesDistinctAtRuntime() {
    SourceFile entry =
        source(
            "src/app/Main.norm",
            "package app import first.value as left import second.value as right "
                + "Void main() { printLine(left()) printLine(right()) }");
    SourceFile first = source("src/first/Value.norm", "package first Integer value() { return 1 }");
    SourceFile second =
        source("src/second/Value.norm", "package second Integer value() { return 2 }");
    CompilationResult result =
        new Compiler()
            .compile(
                new CompilationRequest(
                    entry.id(), List.of(second, entry, first), Set.of(first.id(), second.id())));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    StringWriter output = new StringWriter();
    new ProgramRunner().run(result.program().orElseThrow(), new PrintWriter(output));
    assertTrue(output.toString().equals(String.join(System.lineSeparator(), "1", "2", "")));
  }

  @Test
  void rejectsPrivateImports() {
    SourceFile entry =
        source("src/app/Main.norm", "package app import secrets.hidden Void main() { hidden() }");
    SourceFile library =
        source("src/secrets/Secret.norm", "package secrets private Void hidden() {}");

    CompilationResult result =
        new Compiler()
            .compile(
                new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsImportsFromFilesNotExportedByTheModule() {
    SourceFile entry =
        source(
            "src/app/Main.norm",
            "package app import math.twice Void main() { printLine(twice(4)) }");
    SourceFile library =
        source(
            "src/math/Numbers.norm",
            "package math public Integer twice(Integer value) { return value * 2 }");

    CompilationResult result =
        new Compiler().compile(new CompilationRequest(entry.id(), List.of(entry, library)));

    assertFalse(result.isSuccess());
  }

  @Test
  void preservesCanonicalTypesThroughImportedAliases() {
    SourceFile entry =
        source(
            "src/app/Main.norm",
            "package app import model.Box as RenamedBox import model.create "
                + "Void main() { RenamedBox<Integer> box = create(value: 7) printLine(box.value) }");
    SourceFile library =
        source(
            "src/model/Box.norm",
            "package model public class Box<T> { T value } "
                + "public Box<T> create<T>(T value) { return Box<T>(value: value) }");

    CompilationResult result =
        new Compiler()
            .compile(
                new CompilationRequest(entry.id(), List.of(entry, library), Set.of(library.id())));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void keepsPrivateDeclarationsFileLocalWithinOnePackage() {
    SourceFile entry =
        source("src/items/Main.norm", "package items Void main() { Hidden value = Hidden() }");
    SourceFile library = source("src/items/Hidden.norm", "package items private class Hidden {}");

    CompilationResult result =
        new Compiler().compile(new CompilationRequest(entry.id(), List.of(entry, library)));

    assertFalse(result.isSuccess());
  }

  @Test
  void rejectsPrivateTypesInPublicSignatures() {
    SourceFile source =
        source(
            "src/model/Api.norm",
            "package model private class Hidden {} public Hidden reveal() { return Hidden() } "
                + "Void main() {}");

    CompilationResult result = new Compiler().compile(source);

    assertFalse(result.isSuccess());
    assertTrue(
        result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("public signature")));
  }

  private static SourceFile source(String path, String text) {
    return SourceFile.of(Path.of(path), text);
  }
}
