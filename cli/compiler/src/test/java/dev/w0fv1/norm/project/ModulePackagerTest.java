package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
  void packagesBinaryModuleResourcesAndMaterializesThemForConsumers() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("library/example/assets"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          return module(name: "example.assets", version: 1, exports: ["Library"])
        }
        """);
    Files.writeString(
        module.resolve("Library.norm"),
        """
        package example.assets
        public String libraryName() { return "assets" }
        """);
    byte[] icon = new byte[] {0, 1, 2, -1};
    Path resource = module.resolve("resources/public/icon.bin");
    Files.createDirectories(resource.getParent());
    Files.write(resource, icon);
    Path repository = temporaryDirectory.resolve("repository");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    ModulePackager.PackagedModule packaged;
    try (ProjectLoader projects = environment.projectLoader()) {
      packaged = new ModulePackager(projects).packageModule(modulePath, repository);
    }
    try (ZipFile archive = new ZipFile(packaged.archive().toFile())) {
      var entry = archive.getEntry("resources/public/icon.bin");
      assertTrue(entry != null);
      assertTrue(java.util.Arrays.equals(icon, archive.getInputStream(entry).readAllBytes()));
    }

    Path app = Files.createDirectories(temporaryDirectory.resolve("consumer/sample"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "sample",
            version: 1,
            exports: ["Main"],
            dependencies: [dependency(name: "example.assets", version: 1)]
          )
        }
        """);
    Files.writeString(
        entry,
        """
        package sample
        import example.assets.libraryName
        Void main() { printLine(libraryName()) }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            consumerEnvironment.projectLoader(repository),
            consumerEnvironment.compilerSession(),
            backend)) {
      var result = launcher.compile(entry);
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertTrue(
        java.util.Arrays.equals(
            icon,
            Files.readAllBytes(
                app.getParent().resolve("build/norm/java/classes/public/icon.bin"))));
  }

  @Test
  void packagesARuntimeAdapterWithoutAPublicApi() throws Exception {
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
    Files.writeString(
        module.resolve("Main.norm"),
        """
        package empty.adapter
        Void main() {}
        """);
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (ProjectLoader projects =
        environment.projectLoader(temporaryDirectory.resolve("maven-cache"))) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);

      ModulePackager.PackagedModule packaged =
          new ModulePackager(projects)
              .packageModule(modulePath, temporaryDirectory.resolve("repository"));
      assertTrue(Files.isRegularFile(packaged.archive()));
      try (ZipFile archive = new ZipFile(packaged.archive().toFile())) {
        assertEquals(
            0, archive.stream().filter(entry -> entry.getName().startsWith("sources/")).count());
      }
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
    Path unavailableJarCache = temporaryDirectory.resolve("unavailable-jar-cache");
    Files.writeString(unavailableJarCache, "not a repository");
    ProjectEnvironment analysisEnvironment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (ProjectLoader projects =
        analysisEnvironment.projectLoader(repository, unavailableJarCache)) {
      ProjectSourceSet sourceSet = projects.loadForAnalysis(entry);

      assertTrue(sourceSet.jarBindings().isEmpty());
      assertEquals(1, sourceSet.bindingSourceDocuments().size());
      assertTrue(
          sourceSet.sources().stream()
              .anyMatch(source -> source.displayName().endsWith("StringUtils.norm")));
    }
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
  void replacesABindingNarWithAPureNormNarWithoutChangingTheConsumer() throws Exception {
    Path binding = Files.createDirectories(temporaryDirectory.resolve("binding/commons/lang"));
    Path bindingModule = binding.resolve("module.norm");
    Files.writeString(
        bindingModule,
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
    Path bindingRepository = temporaryDirectory.resolve("binding-repository");
    ProjectEnvironment bindingEnvironment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (ProjectLoader projects =
        bindingEnvironment.projectLoader(temporaryDirectory.resolve("binding-cache"))) {
      new ModuleBindingResolutionService(projects).resolve(bindingModule);
      new ModulePackager(projects).packageModule(bindingModule, bindingRepository);
    }

    Path pure = Files.createDirectories(temporaryDirectory.resolve("pure/commons/lang"));
    Path pureModule = pure.resolve("module.norm");
    Files.writeString(
        pureModule,
        """
        Module module() {
          return module(name: "commons.lang", version: 1, exports: ["StringUtils"])
        }
        """);
    Files.writeString(
        pure.resolve("StringUtils.norm"),
        """
        package commons.lang

        public String? stringUtilsReverse(String? value) {
          if value == null {
            return null
          }
          return "mroN"
        }
        """);
    Path pureRepository = temporaryDirectory.resolve("pure-repository");
    ProjectEnvironment pureEnvironment = ProjectEnvironment.bootstrap(new NormRuntime());
    ModulePackager.PackagedModule packaged;
    try (ProjectLoader projects = pureEnvironment.projectLoader()) {
      packaged = new ModulePackager(projects).packageModule(pureModule, pureRepository);
    }
    String pom = Files.readString(packaged.pom());
    assertTrue(pom.contains("<packaging>nar</packaging>"));
    assertFalse(pom.contains("commons-lang3"));
    try (ZipFile archive = new ZipFile(packaged.archive().toFile())) {
      assertTrue(archive.getEntry("sources/commons/lang/StringUtils.norm") != null);
      assertTrue(archive.getEntry("binding/java-api.json") == null);
      var manifest =
          JsonParser.parseReader(
                  new java.io.InputStreamReader(
                      archive.getInputStream(archive.getEntry("module.json"))))
              .getAsJsonObject();
      assertFalse(manifest.has("jar"));
    }

    Path app = Files.createDirectories(temporaryDirectory.resolve("replacement-consumer/sample"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        app.resolve("module.norm"),
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
    Files.writeString(
        entry,
        """
        package sample
        import commons.lang.stringUtilsReverse
        Void main() { printLine(stringUtilsReverse("Norm") ?? "missing") }
        """);

    assertEquals("mroN" + System.lineSeparator(), run(bindingRepository, entry));
    assertEquals("mroN" + System.lineSeparator(), run(pureRepository, entry));
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
            dependencies: [exportedDependency(name: "example.base", version: 2)],
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
    try (ZipFile archive = new ZipFile(packaged.archive().toFile())) {
      String manifest =
          new String(archive.getInputStream(archive.getEntry("module.json")).readAllBytes());
      assertTrue(
          JsonParser.parseString(manifest)
              .getAsJsonObject()
              .getAsJsonObject("module")
              .getAsJsonArray("dependencies")
              .get(0)
              .getAsJsonObject()
              .get("exported")
              .getAsBoolean());
    }
  }

  private static String run(Path repository, Path entry) throws Exception {
    StringWriter output = new StringWriter();
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            environment.projectLoader(repository), environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    return output.toString();
  }
}
