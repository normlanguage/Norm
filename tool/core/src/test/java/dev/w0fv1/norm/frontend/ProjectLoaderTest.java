package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectLoaderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void loadsExportedFilesAndTheirPackagePeers() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("sample/app"));
    Path math = Files.createDirectories(root.resolve("sample/math"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry, "package sample.app import sample.math.twice Void main() { printLine(twice(4)) }");
    Files.writeString(
        math.resolve("Numbers.norm"),
        "package sample.math public Integer twice(Integer value) { return helper(value) * 2 }");
    Files.writeString(
        math.resolve("Helper.norm"),
        "package sample.math Integer helper(Integer value) { return value }");
    Files.writeString(
        root.resolve("module.norm"),
        "Module(name: \"sample\", version: 1, exports: [\"math.Numbers\"])");

    CompilationRequest request = new ProjectLoader().load(entry);

    assertEquals(3, request.sources().size());
    assertEquals(1, request.exportedSources().size());
    assertTrue(new Compiler().compile(request).isSuccess());
  }

  @Test
  void leavesOtherPackagesUnavailableWithoutAModuleManifest() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("app"));
    Path math = Files.createDirectories(root.resolve("math"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(entry, "package app import math.twice Void main() { printLine(twice(4)) }");
    Files.writeString(
        math.resolve("Numbers.norm"),
        "package math public Integer twice(Integer value) { return value * 2 }");

    CompilationRequest request = new ProjectLoader().load(entry);

    assertEquals(1, request.sources().size());
    assertFalse(new Compiler().compile(request).isSuccess());
  }

  @Test
  void rejectsMissingExportedFiles() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("sample/app"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(
        root.resolve("module.norm"),
        "Module(name: \"sample\", version: 1, exports: [\"math.Missing\"])");

    assertThrows(IOException.class, () -> new ProjectLoader().load(entry));
  }

  @Test
  void rejectsExportedFilesWithTheWrongPackage() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("sample/app"));
    Path math = Files.createDirectories(root.resolve("sample/math"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(
        math.resolve("Numbers.norm"), "package sample.text public Integer value() { return 1 }");
    Files.writeString(
        root.resolve("module.norm"),
        "Module(name: \"sample\", version: 1, exports: [\"math.Numbers\"])");

    assertThrows(IOException.class, () -> new ProjectLoader().load(entry));
  }

  @Test
  void rejectsExportedFilesWithoutAPackage() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("sample/app"));
    Path math = Files.createDirectories(root.resolve("sample/math"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(math.resolve("Numbers.norm"), "public Integer value() { return 1 }");
    Files.writeString(
        root.resolve("module.norm"),
        "Module(name: \"sample\", version: 1, exports: [\"math.Numbers\"])");

    assertThrows(IOException.class, () -> new ProjectLoader().load(entry));
  }

  @Test
  void keepsPackagePeersInternalUnlessTheyAreExported() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("sample/app"));
    Path math = Files.createDirectories(root.resolve("sample/math"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry, "package sample.app import sample.math.helper Void main() { printLine(helper(4)) }");
    Files.writeString(
        math.resolve("Numbers.norm"),
        "package sample.math public Integer twice(Integer value) { return helper(value) * 2 }");
    Files.writeString(
        math.resolve("Helper.norm"),
        "package sample.math public Integer helper(Integer value) { return value }");
    Files.writeString(
        root.resolve("module.norm"),
        "Module(name: \"sample\", version: 1, exports: [\"math.Numbers\"])");

    CompilationRequest request = new ProjectLoader().load(entry);

    assertFalse(new Compiler().compile(request).isSuccess());
  }

  @Test
  void rejectsPackagePeersWithoutTheDirectoryPackage() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("sample/app"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(app.resolve("Script.norm"), "Void helper() {}");

    assertThrows(IOException.class, () -> new ProjectLoader().load(entry));
  }

  @Test
  void loadsExportedPackageSourceNamedModuleNorm() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("sample/app"));
    Path math = Files.createDirectories(root.resolve("sample/math"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry, "package sample.app import sample.math.answer Void main() { printLine(answer()) }");
    Files.writeString(
        math.resolve("module.norm"), "package sample.math public Integer answer() { return 42 }");
    Files.writeString(
        root.resolve("module.norm"),
        "Module(name: \"sample\", version: 1, exports: [\"math.module\"])");

    CompilationRequest request = new ProjectLoader().load(entry);

    assertEquals(2, request.sources().size());
    assertTrue(new Compiler().compile(request).isSuccess());
  }
}
