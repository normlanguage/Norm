package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CommonsIoBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheCommonsIoNarForPathsFilesAndStreams() throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding/commons-io"))) {
      workspace = workspace.getParent();
    }
    assertTrue(workspace != null, "workspace root is unavailable");
    Path module = workspace.resolve("java-binding/commons-io/commons/io/module.norm");
    Path repository = temporaryDirectory.resolve("repository");
    Path upstream = Files.createDirectories(repository.resolve("commons-io/commons-io/2.22.0"));
    Files.copy(
        workspace.resolve("java-binding/commons-io/commons/io/lib/commons-io-2.22.0.jar"),
        upstream.resolve("commons-io-2.22.0.jar"));
    Files.writeString(
        upstream.resolve("commons-io-2.22.0.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>commons-io</groupId>
          <artifactId>commons-io</artifactId>
          <version>2.22.0</version>
        </project>
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = environment.projectLoader(repository)) {
      new ModulePackager(projects).packageModule(module, repository);
    }

    Path app = Files.createDirectories(temporaryDirectory.resolve("app"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "app",
            version: 1,
            exports: ["Main"],
            dependencies: [dependency(repository: "github", name: "commons.io", version: 1)]
          )
        }
        """);
    Path file = temporaryDirectory.resolve("content.txt");
    Files.writeString(
        entry,
        """
        package app

        import commons.io.fileUtilsDeleteQuietly
        import commons.io.fileUtilsReadFileToStringJavaFileAndCharset
        import commons.io.fileUtilsWriteStringToFileJavaFileAndStringAndCharset
        import commons.io.filenameUtilsGetBaseName
        import commons.io.filenameUtilsGetExtension
        import commons.io.filenameUtilsNormalize
        import commons.io.ioUtilsToInputStreamJavaStringAndCharset
        import std.filesystem.Path
        import std.io.Bytes
        import std.io.InputStream
        import std.io.TextEncoding
        import std.io.decodeText
        import std.io.readAll

        Void main() {
          printLine(filenameUtilsGetBaseName("archive.tar.nar") ?? "")
          printLine(filenameUtilsGetExtension("archive.tar.nar") ?? "")
          printLine(filenameUtilsNormalize("a/../module.norm") ?? "")
          Path file = Path(value: "%s")
          fileUtilsWriteStringToFileJavaFileAndStringAndCharset(
            arg0: file,
            arg1: "Norm NAR",
            arg2: "UTF-8"
          )
          printLine(
            fileUtilsReadFileToStringJavaFileAndCharset(arg0: file, arg1: "UTF-8") ?? ""
          )
          printLine(fileUtilsDeleteQuietly(arg0: file))
          InputStream? input =
            ioUtilsToInputStreamJavaStringAndCharset(arg0: "stream", arg1: "UTF-8")
          if input != null {
            Bytes content = readAll(reader: input, maximumBytes: 16)
            printLine(decodeText(content: content, encoding: TextEncoding.Utf8))
            input.close()
          }
        }
        """
            .formatted(file.toString().replace('\\', '/')));
    StringWriter output = new StringWriter();
    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            consumerEnvironment.projectLoader(repository),
            consumerEnvironment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        String.join(
            System.lineSeparator(),
            "archive.tar",
            "nar",
            "module.norm",
            "Norm NAR",
            "true",
            "stream",
            ""),
        output.toString());
    assertFalse(Files.exists(file));
  }
}
