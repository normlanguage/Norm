package dev.w0fv1.norm.packages;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import dev.w0fv1.norm.documentation.MarkdownReferenceChecker;
import dev.w0fv1.norm.jvm.JarResolver;
import dev.w0fv1.norm.project.ModulePackager;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.ModuleRepositoryId;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
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
    Path modules = directory.resolve("modules");
    Path module = Files.createDirectories(modules.resolve("sample/" + artifact));
    var requirements = new java.util.ArrayList<String>();
    for (var item : JsonParser.parseString(dependencies).getAsJsonArray()) {
      var dependency = item.getAsJsonObject();
      requirements.add(
          "dependency(repository: \"github\", name: \"%s\", version: %d)"
              .formatted(
                  dependency.get("name").getAsString(), dependency.get("version").getAsInt()));
    }
    Path manifest = module.resolve("module.norm");
    Files.writeString(
        manifest,
        "Module module() { module(name: \"sample.%s\", version: %d, exports: [\"api\"], dependencies: [%s]) }"
            .formatted(artifact, version, String.join(",", requirements)));
    Files.writeString(module.resolve("api.norm"), source);
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    var resolver =
        new NormPackageResolver(
            directory.resolve("packaged"), directory.resolve("packaging-cache"), Map.of());
    try (var compiler = environment.compilerSession();
        var projects =
            environment.projectLoader(resolver, new JarResolver(directory.resolve("jars")))) {
      var packaged =
          new ModulePackager(projects, compiler)
              .packageModule(manifest, directory.resolve("packaged"));
      Files.copy(packaged.archive(), archive);
    }
    Files.writeString(
        archive.resolveSibling(archive.getFileName() + ".sha256"),
        Sha256Digest.compute(archive).value());
    return archive;
  }
}
