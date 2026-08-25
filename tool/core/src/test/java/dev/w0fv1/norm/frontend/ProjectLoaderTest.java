package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
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

    CompilationRequest request = new ProjectLoader().load(entry).compilationRequest();

    assertEquals(3, request.sources().size());
    assertEquals(1, request.exportedSources().size());
    assertEquals(root.resolve("module.norm").toUri(), request.unit().uri());
    assertTrue(new CompilerSession().compile(request).isSuccess());
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

    CompilationRequest request = new ProjectLoader().load(entry).compilationRequest();

    assertEquals(1, request.sources().size());
    assertFalse(new CompilerSession().compile(request).isSuccess());
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

    CompilationRequest request = new ProjectLoader().load(entry).compilationRequest();

    assertFalse(new CompilerSession().compile(request).isSuccess());
  }

  @Test
  void rejectsPackagePeersWithoutTheDirectoryPackage() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("sample/app"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(app.resolve("Script.norm"), "Void helper() {}");
    Files.writeString(
        root.resolve("module.norm"), "Module(name: \"sample\", version: 1, exports: [])");

    assertThrows(IOException.class, () -> new ProjectLoader().load(entry));
  }

  @Test
  void treatsPackagedSourceWithoutManifestAsStandalone() throws Exception {
    Path packageDirectory = Files.createDirectories(temporaryDirectory.resolve("sample"));
    Path entry = packageDirectory.resolve("Main.norm");
    Path peer = packageDirectory.resolve("Peer.norm");
    Files.writeString(entry, "package sample Void main() {}");
    Files.writeString(peer, "package sample public Integer value() { return 1 }");

    SourceFile openPeer = SourceFile.of(peer, "package sample public Integer value() { return 2 }");
    ProjectSourceSet sourceSet =
        new ProjectLoader().load(SourceFile.read(entry), List.of(openPeer));

    assertEquals(1, sourceSet.sources().size());
    assertEquals(entry.toAbsolutePath().normalize(), sourceSet.entryPath());
    assertEquals(Set.of(entry.toAbsolutePath().normalize()), sourceSet.inputPaths());
  }

  @Test
  void loadsUnsavedModuleFromEntryManifestAndSourceOverlays() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path entry = root.resolve("sample/app/Main.norm");
    Path library = root.resolve("sample/util/Identity.norm");
    Path manifest = root.resolve("module.norm");
    Files.createDirectories(entry.getParent());
    Files.createDirectories(library.getParent());
    Files.writeString(
        manifest, "Module(name: \"disk\", version: 0, exports: [\"missing.Source\"])");
    SourceFile openEntry =
        SourceFile.of(
            entry, "package sample.app import sample.util.identity Void main() { identity(1) }");
    SourceFile openLibrary =
        SourceFile.of(
            library, "package sample.util public Integer identity(Integer value) { return value }");
    SourceFile openManifest =
        SourceFile.of(
            manifest, "Module(name: \"sample\", version: 1, exports: [\"util.Identity\"])");

    ProjectSourceSet sourceSet =
        new ProjectLoader().load(openEntry, List.of(openManifest, openLibrary));
    CompilationRequest request = sourceSet.compilationRequest();

    assertEquals(2, sourceSet.sources().size());
    assertSame(openEntry, request.entrySource());
    assertSame(
        openLibrary,
        sourceSet.sources().stream()
            .filter(source -> source.id().equals(openLibrary.id()))
            .findFirst()
            .orElseThrow());
    assertEquals(Set.of(openLibrary.id()), request.exportedSources());
    assertTrue(new CompilerSession().compile(request).isSuccess());
  }

  @Test
  void rejectsPackageMismatchInEveryModuleSource() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path entry = root.resolve("sample/app/Main.norm");
    Path hidden = root.resolve("sample/internal/Hidden.norm");
    Files.createDirectories(entry.getParent());
    Files.createDirectories(hidden.getParent());
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(hidden, "package sample.other Integer hidden() { return 1 }");
    Files.writeString(
        root.resolve("module.norm"), "Module(name: \"sample\", version: 1, exports: [])");

    assertThrows(IOException.class, () -> new ProjectLoader().load(entry));
  }

  @Test
  void rejectsPackageMismatchInUnsavedModuleSource() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path entry = root.resolve("sample/app/Main.norm");
    Path hidden = root.resolve("sample/internal/Hidden.norm");
    Files.createDirectories(entry.getParent());
    Files.createDirectories(hidden.getParent());
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(
        root.resolve("module.norm"), "Module(name: \"sample\", version: 1, exports: [])");
    SourceFile openHidden =
        SourceFile.of(hidden, "package sample.other Integer hidden() { return 1 }");

    assertThrows(
        IOException.class,
        () -> new ProjectLoader().load(SourceFile.read(entry), List.of(openHidden)));
  }

  @Test
  void loadsEveryModuleSourceIntoOneImmutableSourceSet() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("src"));
    Path app = Files.createDirectories(root.resolve("sample/app"));
    Path library = Files.createDirectories(root.resolve("sample/library"));
    Path internal = Files.createDirectories(root.resolve("sample/internal"));
    Path entry = app.resolve("Main.norm");
    Path exported = library.resolve("Value.norm");
    Path privateSource = internal.resolve("Hidden.norm");
    Path manifest = root.resolve("module.norm");
    Files.writeString(
        entry, "package sample.app import sample.library.value Void main() { value() }");
    Files.writeString(exported, "package sample.library public Integer value() { return 1 }");
    Files.writeString(
        privateSource, "package sample.internal public Integer hidden() { return 2 }");
    Files.writeString(
        manifest, "Module(name: \"sample\", version: 1, exports: [\"library.Value\"])");

    ProjectSourceSet sourceSet = new ProjectLoader().load(entry);
    CompilationRequest request = sourceSet.compilationRequest();

    assertEquals(root.toAbsolutePath().normalize(), sourceSet.root());
    assertEquals(3, sourceSet.sources().size());
    assertEquals(
        Set.of(
            entry.toAbsolutePath().normalize(),
            exported.toAbsolutePath().normalize(),
            privateSource.toAbsolutePath().normalize(),
            manifest.toAbsolutePath().normalize()),
        sourceSet.inputPaths());
    assertSame(
        sourceSet.sources().stream()
            .filter(source -> source.path().equals(entry.toAbsolutePath().normalize()))
            .findFirst()
            .orElseThrow(),
        request.entrySource());
    assertEquals(1, request.exportedSources().size());
    assertEquals(manifest.toAbsolutePath().normalize().toUri(), request.unit().uri());
    assertTrue(new CompilerSession().compile(request).isSuccess());
    assertThrows(UnsupportedOperationException.class, () -> sourceSet.sources().clear());
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

    CompilationRequest request = new ProjectLoader().load(entry).compilationRequest();
    ProjectSourceSet packageEntry = new ProjectLoader().load(math.resolve("module.norm"));

    assertEquals(2, request.sources().size());
    assertTrue(new CompilerSession().compile(request).isSuccess());
    assertEquals(root.toAbsolutePath().normalize(), packageEntry.root());
    assertEquals(request.unit(), packageEntry.compilationRequest().unit());
    assertEquals(
        math.resolve("module.norm").toAbsolutePath().normalize(), packageEntry.entryPath());
  }

  @Test
  void excludesNestedModulesFromTheOuterSourceSet() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("outer"));
    Path entry = root.resolve("sample/app/Main.norm");
    Path nestedRoot = root.resolve("vendor");
    Path nestedSource = nestedRoot.resolve("nested/Value.norm");
    Files.createDirectories(entry.getParent());
    Files.createDirectories(nestedSource.getParent());
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(
        root.resolve("module.norm"), "Module(name: \"sample\", version: 1, exports: [])");
    Files.writeString(
        nestedRoot.resolve("module.norm"),
        "Module(name: \"nested\", version: 1, exports: [\"Value\"])");
    Files.writeString(nestedSource, "package wrong.package public Integer value() { return 1 }");

    ProjectSourceSet sourceSet = new ProjectLoader().load(entry);

    assertEquals(
        List.of(SourceFile.read(entry).id()),
        sourceSet.sources().stream().map(SourceFile::id).toList());
    assertFalse(
        sourceSet.inputPaths().contains(nestedRoot.resolve("module.norm").toAbsolutePath()));
    assertFalse(sourceSet.inputPaths().contains(nestedSource.toAbsolutePath()));
  }

  @Test
  void excludesAnUnsavedNestedModuleFromTheOuterSourceSet() throws Exception {
    Path root = Files.createDirectories(temporaryDirectory.resolve("outer"));
    Path entry = root.resolve("sample/app/Main.norm");
    Path nestedManifest = root.resolve("sample/generated/module.norm");
    Path nestedSource = root.resolve("sample/generated/nested/Value.norm");
    Files.createDirectories(entry.getParent());
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(
        root.resolve("module.norm"), "Module(name: \"sample\", version: 1, exports: [])");
    SourceFile openManifest =
        SourceFile.of(nestedManifest, "Module(name: \"nested\", version: 1, exports: [\"Value\"])");
    SourceFile openNestedSource =
        SourceFile.of(nestedSource, "package wrong.package public Integer value() { return 1 }");

    ProjectSourceSet sourceSet =
        new ProjectLoader().load(SourceFile.read(entry), List.of(openManifest, openNestedSource));

    assertEquals(
        List.of(SourceFile.read(entry).id()),
        sourceSet.sources().stream().map(SourceFile::id).toList());
    assertFalse(sourceSet.inputPaths().contains(nestedManifest.toAbsolutePath()));
    assertFalse(sourceSet.inputPaths().contains(nestedSource.toAbsolutePath()));
  }
}
