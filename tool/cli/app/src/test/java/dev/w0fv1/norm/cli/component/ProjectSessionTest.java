package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.w0fv1.norm.frontend.CompilationEnvironment;
import dev.w0fv1.norm.frontend.Compiler;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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
        app, "package sample.app import sample.util.identity void main() { print(identity(1)) }");
    Files.writeString(
        library, "package sample.util public int identity(int value) { return value }");
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
}
