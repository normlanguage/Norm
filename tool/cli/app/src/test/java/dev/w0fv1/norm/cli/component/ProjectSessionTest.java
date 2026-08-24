package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.CompilationEnvironment;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectSessionTest {
  @Test
  void oneRefreshAnalyzesEveryOpenDocumentIntoOneSnapshot(@TempDir Path directory)
      throws Exception {
    Path app = directory.resolve("sample/app/Main.norm");
    Path library = directory.resolve("sample/util/Identity.norm");
    Files.createDirectories(app.getParent());
    Files.createDirectories(library.getParent());
    Files.writeString(
        directory.resolve("module.norm"),
        "Module(name: \"sample\", version: 1, exports: [\"util.Identity\"])");
    Files.writeString(
        app,
        "package sample.app import sample.util.identity Void main() { printLine(identity(1)) }");
    Files.writeString(
        library, "package sample.util public Integer identity(Integer value) { return value }");
    SourceFile appSource = SourceFile.read(app);
    SourceFile librarySource = SourceFile.read(library);
    Map<Path, SourceFile> open = new LinkedHashMap<>();
    open.put(ProjectSession.normalize(app), appSource);
    open.put(ProjectSession.normalize(library), librarySource);
    AtomicInteger analyses = new AtomicInteger();
    LanguageService language =
        new LanguageService(
            new Compiler(CompilationEnvironment.create(() -> {}, analyses::incrementAndGet)));

    ProjectSession session = ProjectSession.load(language, appSource, open, 41);

    assertEquals(1, analyses.get());
    assertEquals(41, session.revision());
    assertSame(
        session.snapshot().semanticModel(),
        session.snapshot().document(librarySource.id()).orElseThrow().projectModel());
    session.analysis(appSource);
    session.analysis(librarySource);
    assertEquals(1, analyses.get());
  }

  @Test
  void excludesOpenDocumentsFromOtherModules(@TempDir Path directory) throws Exception {
    Path firstRoot = directory.resolve("first");
    Path secondRoot = directory.resolve("second");
    Path first = firstRoot.resolve("sample/Main.norm");
    Path second = secondRoot.resolve("sample/Main.norm");
    Files.createDirectories(first.getParent());
    Files.createDirectories(second.getParent());
    Files.writeString(
        firstRoot.resolve("module.norm"), "Module(name: \"sample\", version: 1, exports: [])");
    Files.writeString(
        secondRoot.resolve("module.norm"), "Module(name: \"sample\", version: 1, exports: [])");
    Files.writeString(first, "package sample Void main() {}");
    Files.writeString(second, "package sample Void main() {}");
    SourceFile firstSource = SourceFile.read(first);
    SourceFile secondSource = SourceFile.read(second);
    Map<Path, SourceFile> open = new LinkedHashMap<>();
    open.put(ProjectSession.normalize(first), firstSource);
    open.put(ProjectSession.normalize(second), secondSource);

    ProjectSession session = ProjectSession.load(new LanguageService(), firstSource, open, 1);

    assertFalse(session.inputs().contains(ProjectSession.normalize(second)));
  }

  @Test
  void treatsPackagedSourceWithoutManifestAsStandalone(@TempDir Path directory) throws Exception {
    Path packageDirectory = directory.resolve("sample");
    Path first = packageDirectory.resolve("First.norm");
    Path second = packageDirectory.resolve("Second.norm");
    Files.createDirectories(packageDirectory);
    Files.writeString(first, "package sample Void main() {}");
    Files.writeString(second, "package sample Void main() {}");
    SourceFile firstSource = SourceFile.read(first);
    SourceFile secondSource = SourceFile.read(second);
    Map<Path, SourceFile> open = new LinkedHashMap<>();
    open.put(ProjectSession.normalize(first), firstSource);
    open.put(ProjectSession.normalize(second), secondSource);

    ProjectSession session = ProjectSession.load(new LanguageService(), firstSource, open, 1);

    assertEquals(Set.of(ProjectSession.normalize(first)), session.inputs());
    assertFalse(
        session.analysis(firstSource).diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("already declared")));
  }

  @Test
  void includesPackageSourceNamedModuleNormInTheProjectSnapshot(@TempDir Path directory)
      throws Exception {
    Path entry = directory.resolve("sample/app/Main.norm");
    Path packageSource = directory.resolve("sample/internal/module.norm");
    Files.createDirectories(entry.getParent());
    Files.createDirectories(packageSource.getParent());
    Files.writeString(
        directory.resolve("module.norm"), "Module(name: \"sample\", version: 1, exports: [])");
    Files.writeString(entry, "package sample.app Void main() {}");
    Files.writeString(packageSource, "package sample.internal public Integer value() { return 1 }");
    SourceFile entrySource = SourceFile.read(entry);
    SourceFile packageSourceFile = SourceFile.read(packageSource);

    ProjectSession session =
        ProjectSession.load(
            new LanguageService(),
            entrySource,
            Map.of(ProjectSession.normalize(entry), entrySource),
            1);

    assertTrue(session.inputs().contains(ProjectSession.normalize(packageSource)));
    assertTrue(session.snapshot().document(packageSourceFile.id()).isPresent());
  }

  @Test
  void loadsUnsavedModuleDirectlyFromOpenSources(@TempDir Path directory) throws Exception {
    Path entry = directory.resolve("sample/app/Main.norm");
    Path library = directory.resolve("sample/util/Identity.norm");
    Path manifest = directory.resolve("module.norm");
    Files.createDirectories(entry.getParent());
    Files.createDirectories(library.getParent());
    SourceFile entrySource =
        SourceFile.of(
            entry, "package sample.app import sample.util.identity Void main() { identity(1) }");
    SourceFile librarySource =
        SourceFile.of(
            library, "package sample.util public Integer identity(Integer value) { return value }");
    SourceFile manifestSource =
        SourceFile.of(
            manifest, "Module(name: \"sample\", version: 1, exports: [\"util.Identity\"])");
    Map<Path, SourceFile> open = new LinkedHashMap<>();
    open.put(ProjectSession.normalize(entry), entrySource);
    open.put(ProjectSession.normalize(library), librarySource);
    open.put(ProjectSession.normalize(manifest), manifestSource);

    ProjectSession session = ProjectSession.load(new LanguageService(), entrySource, open, 1);

    assertEquals(
        Set.of(
            ProjectSession.normalize(entry),
            ProjectSession.normalize(library),
            ProjectSession.normalize(manifest)),
        session.inputs());
    assertTrue(session.snapshot().document(librarySource.id()).isPresent());
    assertTrue(session.analysis(entrySource).diagnostics().isEmpty());
  }
}
