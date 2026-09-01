package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModulePackagerTest {
  @TempDir Path temporaryDirectory;

  @Test
  void rejectsAnAdapterWithoutAPublicApi() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("sources/empty/adapter"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          return module(
            name: "empty.adapter",
            version: 1,
            binding: jarBinding(
              target: mavenJar(
                group: "org.apache.commons",
                artifact: "commons-lang3",
                version: "3.20.0"
              ),
              api: []
            )
          )
        }
        """);
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (ProjectLoader projects =
        environment.projectLoader(temporaryDirectory.resolve("maven-cache"))) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);

      var failure =
          assertThrows(
              java.io.IOException.class,
              () ->
                  new ModulePackager(projects)
                      .packageModule(modulePath, temporaryDirectory.resolve("repository")));

      assertTrue(failure.getMessage().contains("public API"));
    }
  }

  @Test
  void packagesAResolvedCommonsLangAdapterAsANar() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("sources/commons/lang"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          return module(
            name: "commons.lang",
            version: 1,
            binding: jarBinding(
              target: mavenJar(
                group: "org.apache.commons",
                artifact: "commons-lang3",
                version: "3.20.0"
              ),
              api: [
                jarType(
                  name: "StringUtils",
                  members: [],
                  overloads: [
                    jarOverload(name: "reverse", parameterTypes: ["java.lang.String"])
                  ]
                )
              ]
            )
          )
        }
        """);
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    Path repository = temporaryDirectory.resolve("repository");

    ModulePackager.PackagedModule packaged;
    try (ProjectLoader projects =
        environment.projectLoader(temporaryDirectory.resolve("maven-cache"))) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);
      packaged = new ModulePackager(projects).packageModule(modulePath, repository);
    }

    assertEquals(
        repository.resolve("commons/lang/1/lang-1.nar").toAbsolutePath(), packaged.archive());
    assertEquals(repository.resolve("commons/lang/1/lang-1.pom").toAbsolutePath(), packaged.pom());
    String pom = Files.readString(packaged.pom());
    assertTrue(pom.contains("<groupId>commons</groupId>"));
    assertTrue(pom.contains("<artifactId>lang</artifactId>"));
    assertTrue(pom.contains("<packaging>nar</packaging>"));
    assertTrue(pom.contains("<artifactId>commons-lang3</artifactId>"));
    try (ZipFile archive = new ZipFile(packaged.archive().toFile())) {
      assertTrue(archive.getEntry("module.json") != null);
      assertTrue(archive.getEntry("sources/commons/lang/StringUtils.norm") != null);
      var manifest =
          JsonParser.parseReader(
                  new java.io.InputStreamReader(
                      archive.getInputStream(archive.getEntry("module.json"))))
              .getAsJsonObject();
      var api = manifest.getAsJsonObject("jar").getAsJsonArray("api");
      assertEquals("StringUtils", api.get(0).getAsJsonObject().get("name").getAsString());
      assertEquals(0, api.get(0).getAsJsonObject().getAsJsonArray("members").size());
      var overload =
          api.get(0).getAsJsonObject().getAsJsonArray("overloads").get(0).getAsJsonObject();
      assertEquals("reverse", overload.get("name").getAsString());
      assertEquals(
          "java.lang.String", overload.getAsJsonArray("parameterTypes").get(0).getAsString());
      var report = archive.getEntry("binding/java-api.json");
      assertTrue(report != null);
      var json =
          JsonParser.parseReader(new java.io.InputStreamReader(archive.getInputStream(report)))
              .getAsJsonObject();
      assertEquals(1, json.get("formatVersion").getAsInt());
      assertEquals(64, json.get("apiId").getAsString().length());
      assertTrue(json.getAsJsonObject("summary").get("unsupportedMembers").getAsInt() > 0);
      assertTrue(json.getAsJsonArray("types").size() > 0);
    }

    Path appRoot = Files.createDirectories(temporaryDirectory.resolve("consumer/sample"));
    Path entry = appRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package sample
        import commons.lang.stringUtilsReverse
        Void main() { printLine(stringUtilsReverse("Norm") ?? "missing") }
        """);
    Files.writeString(
        appRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "sample",
            version: 1,
            exports: ["Main"],
            dependencies: [dependency(name: "commons.lang", version: 1)]
          )
        }
        """);
    StringWriter output = new StringWriter();
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            consumerEnvironment.projectLoader(repository),
            consumerEnvironment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals("mroN" + System.lineSeparator(), output.toString());
  }

  @Test
  void writesModuleDependenciesAsNarDependencies() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("sources/example/adapter"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          return module(
            name: "example.adapter",
            version: 1,
            dependencies: [dependency(name: "example.base", version: 2)],
            binding: jarBinding(
              target: mavenJar(
                group: "org.apache.commons",
                artifact: "commons-lang3",
                version: "3.20.0"
              ),
              api: [jarType(name: "StringUtils", members: ["reverse"])]
            )
          )
        }
        """);
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    ModulePackager.PackagedModule packaged;
    try (ProjectLoader projects =
        environment.projectLoader(temporaryDirectory.resolve("maven-cache"))) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);
      packaged =
          new ModulePackager(projects)
              .packageModule(modulePath, temporaryDirectory.resolve("repository"));
    }

    String pom = Files.readString(packaged.pom());
    assertTrue(
        pom.contains(
            """
                <dependency>
                  <groupId>example</groupId>
                  <artifactId>base</artifactId>
                  <version>2</version>
                  <type>nar</type>
                  <scope>runtime</scope>
                </dependency>
            """));
  }
}
