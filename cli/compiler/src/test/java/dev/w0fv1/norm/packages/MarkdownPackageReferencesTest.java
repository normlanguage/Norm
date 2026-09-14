package dev.w0fv1.norm.packages;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.documentation.MarkdownReferenceChecker;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.ModuleRepositoryId;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MarkdownPackageReferencesTest {
  @TempDir Path directory;

  @Test
  void downloadsExactVersionsAndChecksArchiveDeclarationsWithDependencies() throws Exception {
    Path registry = directory.resolve("registry.json");
    Files.writeString(
        registry,
        """
        {"formatVersion":1,"packages":[
          {"name":"sample","owner":"owner","repository":"parent"},
          {"name":"sample.library","owner":"owner","repository":"library"},
          {"name":"sample.base","owner":"owner","repository":"base"}
        ]}
        """);
    Path remote = Files.createDirectories(directory.resolve("remote"));
    archive(remote, "base", 1, "package sample.base\nvalue Item { Integer code }", "[]");
    String dependency =
        "[{\"repository\":\"github\",\"name\":\"sample.base\",\"version\":1,\"exported\":false}]";
    Path first =
        archive(
            remote,
            "library",
            1,
            "package sample.library\nimport sample.base.Item\nItem make() { return Item(code: 1) }",
            dependency);
    archive(
        remote, "library", 2, "package sample.library\nInteger replacement() { return 2 }", "[]");
    Path docs = Files.createDirectories(directory.resolve("docs"));
    Path markdown = docs.resolve("guide.md");
    Files.writeString(
        markdown, "@github.sample.library.api.make#1\n@github.sample.library.api.replacement#2\n");
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    var packages =
        new NormPackageResolver(
            directory.resolve("local"),
            directory.resolve("cache"),
            Map.of(
                ModuleRepositoryId.GITHUB,
                new GitHubPackageRepository(registry.toUri(), remote.toUri())));
    try (var projects =
            environment.projectLoader(packages, new JarResolver(directory.resolve("jars")));
        var compiler = environment.compilerSession()) {
      var checker = new MarkdownReferenceChecker(projects, compiler);
      var result = checker.check(docs, Optional.empty());
      assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
      assertTrue(
          Files.isRegularFile(directory.resolve("cache/github/sample/library/1/library-1.nar")));
      Files.delete(first);
      assertTrue(checker.check(docs, Optional.empty()).diagnostics().isEmpty());
      Files.writeString(
          markdown,
          "@github.sample.library.api.make#2\n@github.sample.library.api.make#3\n@github.unknown.api.Type#1\n");
      result = checker.check(docs, Optional.empty());
      assertEquals(3, result.diagnostics().size(), result.diagnostics().toString());
      assertTrue(result.diagnostics().getFirst().message().contains("does not exist"));
      assertTrue(result.diagnostics().getLast().message().contains("no registered module"));
    }
  }

  private Path archive(
      Path remote, String artifact, int version, String source, String dependencies)
      throws Exception {
    Path archive =
        remote.resolve(
            "owner/"
                + artifact
                + "/releases/download/v"
                + version
                + "/"
                + artifact
                + "-"
                + version
                + ".nar");
    Files.createDirectories(archive.getParent());
    String manifest =
        """
        {"formatVersion":%d,"module":{"name":"sample.%s","version":%d,"exports":["api"],"dependencies":%s}}
        """
            .formatted(
                dev.w0fv1.norm.value.ModuleArchiveFormat.FORMAT_VERSION,
                artifact,
                version,
                dependencies);
    try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      for (var entry :
          Map.of("module.json", manifest, "sources/sample/" + artifact + "/api.norm", source)
              .entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    }
    Files.writeString(
        archive.resolveSibling(archive.getFileName() + ".sha256"),
        Sha256Digest.compute(archive).value());
    return archive;
  }
}
