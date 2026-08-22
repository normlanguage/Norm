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
            "src/app/Main.norm", "package app import math.twice void main() { print(twice(4)) }");
    SourceFile library =
        source(
            "src/math/Numbers.norm",
            "package math public int twice(int value) { return value * 2 }");

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
            "package check void main() { print(even(8)) } "
                + "bool odd(int value) { if value == 0 { return false } return even(value - 1) }");
    SourceFile second =
        source(
            "src/check/Even.norm",
            "package check bool even(int value) { if value == 0 { return true } return odd(value - 1) }");

    CompilationResult result =
        new Compiler().compile(new CompilationRequest(entry.id(), List.of(second, entry)));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void executesImportedFunctionsFromTheirOwnSourceFile() {
    SourceFile entry =
        source(
            "src/app/Main.norm", "package app import math.twice void main() { print(twice(6)) }");
    SourceFile library =
        source(
            "src/math/Numbers.norm",
            "package math public int twice(int value) { return value * 2 }");
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
            "package app import math.twice as double void main() { print(double(5)) }");
    SourceFile library =
        source("src/math/Numbers.norm", "package math int twice(int value) { return value * 2 }");
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
  void infersImportedGenericFunctions() {
    SourceFile entry =
        source(
            "src/app/Main.norm",
            "package app import util.identity void main() { String value = identity(\"ok\") print(value) }");
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
                + "void main() { print(left()) print(right()) }");
    SourceFile first = source("src/first/Value.norm", "package first int value() { return 1 }");
    SourceFile second = source("src/second/Value.norm", "package second int value() { return 2 }");
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
        source("src/app/Main.norm", "package app import secrets.hidden void main() { hidden() }");
    SourceFile library =
        source("src/secrets/Secret.norm", "package secrets private void hidden() {}");

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
            "src/app/Main.norm", "package app import math.twice void main() { print(twice(4)) }");
    SourceFile library =
        source(
            "src/math/Numbers.norm",
            "package math public int twice(int value) { return value * 2 }");

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
                + "void main() { RenamedBox<int> box = create(value: 7) print(box.value) }");
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
        source("src/items/Main.norm", "package items void main() { Hidden value = Hidden() }");
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
                + "void main() {}");

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
