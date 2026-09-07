package dev.w0fv1.norm.workspace;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WorkspaceTest {
  @Test
  void analyzesAllStandardLibraryEditsInOneSnapshot(@TempDir Path root) throws Exception {
    Path module = Files.createDirectories(root.resolve("std"));
    Files.writeString(
        module.resolve("module.norm"), dev.w0fv1.norm.stdlib.StandardLibrary.moduleSource().text());
    Path math = Files.createDirectories(module.resolve("math")).resolve("integer.norm");
    Path unit = Files.createDirectories(module.resolve("core")).resolve("unit.norm");
    try (var workspace = new Workspace(ProjectEnvironment.bootstrap(new NormRuntime()))) {
      var mathId = dev.w0fv1.norm.source.DocumentId.of("stdlib:/std/math/integer.norm");
      var unitId = dev.w0fv1.norm.source.DocumentId.of("stdlib:/std/core/unit.norm");
      String mathText =
          workspace.language().standardLibrarySource(mathId).orElseThrow()
              + "\npublic Integer reviewValue() { return 42 }";
      String unitText =
          workspace
                  .language()
                  .standardLibrarySource(unitId)
                  .orElseThrow()
                  .replace(
                      "import std.annotation.Document",
                      "import std.annotation.Document\nimport std.math.reviewValue")
              + "\nInteger reviewUse() { return reviewValue() }";
      workspace.update(math.toUri().toString(), 1, mathText);
      workspace.update(unit.toUri().toString(), 1, unitText);
      workspace.settled().get(30, TimeUnit.SECONDS);
      var first = workspace.document(math.toUri().toString()).get();
      var second = workspace.document(unit.toUri().toString()).get();
      assertFalse(second.analysis().hasErrors(), second.analysis().diagnostics().toString());
      assertSame(first.snapshot(), second.snapshot());
      assertEquals(mathText, second.snapshot().document(mathId).orElseThrow().source().text());
      assertEquals(unitText, first.snapshot().document(unitId).orElseThrow().source().text());
    }
  }

  @Test
  void discardsQueuedDiagnosticsWhenTheirDocumentHasChanged() throws Exception {
    var publishing = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    var versions = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
    try (var workspace = new Workspace(ProjectEnvironment.bootstrap(new NormRuntime()))) {
      workspace.onDiagnostics(
          diagnostics -> {
            if (diagnostics.uri().equals("untitled:barrier")) {
              publishing.countDown();
              try {
                if (!release.await(20, TimeUnit.SECONDS))
                  throw new AssertionError("publication barrier timed out");
              } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
              }
            } else if (diagnostics.uri().equals("untitled:queued"))
              versions.add(diagnostics.version());
          });
      workspace.update("untitled:barrier", 1, "Void main() {}");
      assertTrue(publishing.await(20, TimeUnit.SECONDS));
      try {
        workspace.update("untitled:queued", 1, "Void main() { missing() }");
        assertEquals(1, workspace.document("untitled:queued").get(20, TimeUnit.SECONDS).version());
        workspace.update("untitled:queued", 2, "Void main() {}");
        assertEquals(2, workspace.document("untitled:queued").get(20, TimeUnit.SECONDS).version());
      } finally {
        release.countDown();
      }
      workspace.settled().get(20, TimeUnit.SECONDS);
      assertEquals(java.util.List.of(2), versions);
    } finally {
      release.countDown();
    }
  }

  @Test
  void publishesOneSnapshotForAllOpenProjectDocuments(@TempDir Path root) throws Exception {
    Path module = Files.createDirectories(root.resolve("sample"));
    Files.writeString(
        module.resolve("module.norm"),
        "Module module() { return module(name: \"sample\", version: 1) }");
    Path main =
        Files.writeString(module.resolve("Main.norm"), "package sample Void main() { answer() }");
    Path library =
        Files.writeString(
            module.resolve("Value.norm"), "package sample Integer answer() { return 1 }");
    try (var workspace = new Workspace(ProjectEnvironment.bootstrap(new NormRuntime()))) {
      workspace.update(main.toUri().toString(), 1, Files.readString(main));
      workspace.update(library.toUri().toString(), 1, Files.readString(library));
      workspace.settled().get(20, TimeUnit.SECONDS);
      var before = workspace.document(main.toUri().toString()).get();
      workspace.update(
          library.toUri().toString(), 2, "package sample String answer() { return \"changed\" }");
      workspace.settled().get(20, TimeUnit.SECONDS);
      var first = workspace.document(main.toUri().toString()).get();
      var second = workspace.document(library.toUri().toString()).get();
      assertSame(first.snapshot(), second.snapshot());
      assertNotSame(before.snapshot(), first.snapshot());
      assertEquals(2, second.version());
      assertTrue(second.source().text().contains("changed"));
      assertFalse(first.analysis().hasErrors(), first.analysis().diagnostics().toString());
    }
  }

  @Test
  void rejectsOldVersionsAndWorkFromAClosedDocument() throws Exception {
    String uri = "untitled:epoch";
    try (var workspace = new Workspace(ProjectEnvironment.bootstrap(new NormRuntime()))) {
      workspace.update(uri, 8, "Void main() { missing() }");
      workspace.closeDocument(uri);
      workspace.update(uri, 1, "Void main() {}");
      workspace.update(uri, 0, "Void main() { old() }");
      workspace.settled().get(20, TimeUnit.SECONDS);
      var document = workspace.document(uri).get();
      assertEquals(1, document.version());
      assertFalse(document.analysis().hasErrors());
      workspace.closeDocument(uri);
      workspace.settled().get(20, TimeUnit.SECONDS);
      assertNull(workspace.document(uri).get());
    }
  }
}
