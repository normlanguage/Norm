package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectSessionTest {
  @Test
  void oneRefreshAnalyzesEveryOpenDocumentIntoOneSnapshot(@TempDir Path directory)
      throws Exception {
    Path app = directory.resolve("sample/Main.norm");
    Path library = directory.resolve("sample/util/Identity.norm");
    Files.createDirectories(app.getParent());
    Files.createDirectories(library.getParent());
    Files.writeString(
        directory.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: [\"util.Identity\"]) }");
    Files.writeString(
        app, "package sample import sample.util.identity Void main() { printLine(identity(1)) }");
    Files.writeString(
        library, "package sample.util public Integer identity(Integer value) { return value }");
    SourceFile appSource = SourceFile.read(app);
    SourceFile librarySource = SourceFile.read(library);
    Map<Path, SourceFile> open = new LinkedHashMap<>();
    open.put(ProjectSession.normalize(app), appSource);
    open.put(ProjectSession.normalize(library), librarySource);
    ProjectSession session = load(appSource, open, 41);

    assertEquals(41, session.revision());
    assertSame(
        session.snapshot().semanticModel(),
        session.snapshot().document(librarySource.id()).orElseThrow().projectModel());
    session.analysis(appSource);
    session.analysis(librarySource);
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
        firstRoot.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: []) }");
    Files.writeString(
        secondRoot.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: []) }");
    Files.writeString(first, "package sample Void main() {}");
    Files.writeString(second, "package sample Void main() {}");
    SourceFile firstSource = SourceFile.read(first);
    SourceFile secondSource = SourceFile.read(second);
    Map<Path, SourceFile> open = new LinkedHashMap<>();
    open.put(ProjectSession.normalize(first), firstSource);
    open.put(ProjectSession.normalize(second), secondSource);

    ProjectSession session = load(firstSource, open, 1);

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

    ProjectSession session = load(firstSource, open, 1);

    assertEquals(Set.of(ProjectSession.normalize(first)), session.inputs());
    assertFalse(
        session.analysis(firstSource).diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.message().contains("already declared")));
  }

  @Test
  void includesPackageSourceNamedModuleNormInTheProjectSnapshot(@TempDir Path directory)
      throws Exception {
    Path entry = directory.resolve("sample/Main.norm");
    Path packageSource = directory.resolve("sample/internal/module.norm");
    Files.createDirectories(entry.getParent());
    Files.createDirectories(packageSource.getParent());
    Files.writeString(
        directory.resolve("sample/module.norm"),
        "Module module() { return module(name: \"sample\", version: 1, exports: []) }");
    Files.writeString(entry, "package sample Void main() {}");
    Files.writeString(packageSource, "package sample.internal public Integer value() { return 1 }");
    SourceFile entrySource = SourceFile.read(entry);
    SourceFile packageSourceFile = SourceFile.read(packageSource);

    ProjectSession session =
        load(entrySource, Map.of(ProjectSession.normalize(entry), entrySource), 1);

    assertTrue(session.inputs().contains(ProjectSession.normalize(packageSource)));
    assertTrue(session.snapshot().document(packageSourceFile.id()).isPresent());
  }

  @Test
  void loadsUnsavedModuleDirectlyFromOpenSources(@TempDir Path directory) throws Exception {
    Path entry = directory.resolve("sample/Main.norm");
    Path library = directory.resolve("sample/util/Identity.norm");
    Path module = directory.resolve("sample/module.norm");
    Files.createDirectories(entry.getParent());
    Files.createDirectories(library.getParent());
    SourceFile entrySource =
        SourceFile.of(
            entry, "package sample import sample.util.identity Void main() { identity(1) }");
    SourceFile librarySource =
        SourceFile.of(
            library, "package sample.util public Integer identity(Integer value) { return value }");
    SourceFile moduleSource =
        SourceFile.of(
            module,
            "Module module() { return module(name: \"sample\", version: 1, exports: [\"util.Identity\"]) }");
    Map<Path, SourceFile> open = new LinkedHashMap<>();
    open.put(ProjectSession.normalize(entry), entrySource);
    open.put(ProjectSession.normalize(library), librarySource);
    open.put(ProjectSession.normalize(module), moduleSource);

    ProjectSession session = load(entrySource, open, 1);

    assertEquals(
        Set.of(
            ProjectSession.normalize(entry),
            ProjectSession.normalize(library),
            ProjectSession.normalize(module)),
        session.inputs());
    assertTrue(session.snapshot().document(librarySource.id()).isPresent());
    assertTrue(session.analysis(entrySource).diagnostics().isEmpty());
  }

  @Test
  void analyzesDependenciesDeclaredInsideASingleApplicationFile(@TempDir Path directory)
      throws Exception {
    Path entry = directory.resolve("web.norm");
    Files.writeString(
        entry,
        """
        package hello.web
        import base.answer
        Module module() {
          return module(
            name: "hello.web",
            version: 1,
            dependencies: [
              dependency(repository: "github", name: "base", version: 1)
            ]
          )
        }
        Void main() { printLine(answer().toString()) }
        """);
    Path dependency = directory.resolve("dependencies/base");
    Files.createDirectories(dependency);
    Files.writeString(
        dependency.resolve("module.norm"),
        "Module module() { return module(name: \"base\", version: 1, exports: [\"Value\"]) }");
    Files.writeString(
        dependency.resolve("Value.norm"), "package base public Integer answer() { return 42 }");
    SourceFile entrySource = SourceFile.read(entry);

    ProjectSession session =
        load(entrySource, Map.of(ProjectSession.normalize(entry), entrySource), 1);

    assertTrue(session.analysis(entrySource).diagnostics().isEmpty());
    assertTrue(session.inputs().contains(ProjectSession.normalize(entry)));
    assertTrue(
        session.inputs().contains(ProjectSession.normalize(dependency.resolve("module.norm"))));
  }

  private static ProjectSession load(
      SourceFile entry, Map<Path, SourceFile> openSources, long revision) throws Exception {
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (LanguageService language = new LanguageService(environment.compilerSession());
        var projects = environment.projectLoader()) {
      return ProjectSession.load(language, projects, entry, openSources, revision);
    }
  }
}
