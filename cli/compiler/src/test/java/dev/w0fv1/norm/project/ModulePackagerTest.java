package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.testing.MavenTestRepository;
import dev.w0fv1.norm.value.JdkModuleTarget;
import dev.w0fv1.norm.value.ModuleArchiveFormat;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ModulePackagerTest {
  @TempDir Path temporaryDirectory;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"di-4", "theme-2"})
  void readsReleasedVersionFourModuleArchives(String fixture) throws Exception {
    Path archive = temporaryDirectory.resolve(fixture + ".nar");
    try (var source =
        getClass().getResourceAsStream("/fixtures/published-v4/" + fixture + ".nar")) {
      assertNotNull(source);
      Files.copy(source, archive);
    }
    var restored = new dev.w0fv1.norm.project.ModuleArchiveReader().read(archive);
    assertEquals(fixture.equals("di-4") ? "di" : "ui.theme", restored.descriptor().name());
    assertFalse(restored.sources().isEmpty());
    if (fixture.equals("theme-2")) {
      assertFalse(restored.publicTypes().isEmpty());
      var binding = restored.binding().orElseThrow();
      assertTrue(
          binding.descriptor().binding().orElseThrow().api().stream()
              .allMatch(type -> type.alias().isEmpty()));
      try (var zip = new java.util.zip.ZipFile(archive.toFile())) {
        byte[] bytes =
            zip.getInputStream(zip.getEntry(dev.w0fv1.norm.jvm.PublishedJarBinding.ENTRY))
                .readAllBytes();
        assertThrows(
            IOException.class,
            () ->
                dev.w0fv1.norm.jvm.PublishedJarBinding.decode(
                    bytes,
                    restored.descriptor(),
                    binding.api().apiId(),
                    dev.w0fv1.norm.jvm.PublishedJarBinding.ABI));
        assertThrows(
            IOException.class,
            () ->
                dev.w0fv1.norm.jvm.PublishedJarBinding.decode(
                    bytes,
                    restored.descriptor(),
                    binding.api().apiId(),
                    "norm-java-binding-unknown"));
      }
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void privateBindingAliasesStayPrivateForSourceAndArchiveConsumers(boolean packaged)
      throws Exception {
    Path workspace = Files.createDirectories(temporaryDirectory.resolve("workspace"));
    Path module = Files.createDirectories(workspace.resolve("dependencies/example/hidden"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          module(name: "example.hidden", version: 1, exports: ["Api"],
            binding: jarBinding(target: mavenJar(group: "org.apache.commons", artifact: "commons-lang3", version: "%s"), api: [
              jarType(name: "org.apache.commons.lang3.RandomStringUtils", alias: "internal.Random", members: []),
              jarType(name: "org.apache.commons.lang3.StringUtils", alias: "internal.Text", members: ["reverse"])
            ]))
        }
        """
            .formatted(MavenTestRepository.commonsLang().version()));
    Files.writeString(
        module.resolve("Api.norm"),
        """
        package example.hidden
        import example.hidden.internal.textReverse
        public String reverse(String value) { return textReverse(value)!! }
        """);
    Path app = Files.createDirectories(workspace.resolve("app"));
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() { module(name: "app", version: 1,
          dependencies: [dependency(repository: "github", name: "example.hidden", version: 1)]) }
        """);
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package app
        import example.hidden.reverse
        Void main() { printLine(reverse("abc")) }
        """);
    Path repository = MavenTestRepository.prepare(temporaryDirectory.resolve("repository"));
    var backend = new NormRuntime();
    try (var environment = ProjectEnvironment.bootstrap(backend)) {
      try (var projects = environment.projectLoader(repository)) {
        new ModuleBindingResolutionService(projects).resolve(modulePath);
      }
      if (packaged) {
        try (var projects = environment.projectLoader(repository);
            var compiler = environment.compilerSession()) {
          var artifact =
              new ModulePackager(projects, compiler).packageModule(modulePath, repository);
          var archived = new ModuleArchiveReader().read(artifact.archive());
          assertEquals(java.util.List.of("Api"), archived.descriptor().exports());
          assertTrue(archived.publicTypes().isEmpty());
          assertEquals(java.util.Set.of("example/hidden/Api.norm"), archived.publicSources());
          assertEquals(
              java.util.Optional.of("internal.Text"),
              archived.descriptor().binding().orElseThrow().api().get(1).alias());
          assertTrue(archived.sources().containsKey("example/hidden/internal/Text.norm"));
        }
        Files.move(module, workspace.resolve("published-module"));
      }
      var output = new StringWriter();
      try (var launcher =
          new ApplicationRunner(
              environment.projectLoader(repository), environment.compilerSession(), backend)) {
        var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      }
      assertEquals("cba" + System.lineSeparator(), output.toString());
      Files.writeString(
          entry,
          """
          package app
          import example.hidden.internal.textReverse
          Void main() { printLine(textReverse("abc")) }
          """);
      try (var launcher =
          new ApplicationRunner(
              environment.projectLoader(repository), environment.compilerSession(), backend)) {
        var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(new StringWriter())));
        assertFalse(result.isSuccess());
        assertTrue(
            result.diagnostics().toString().contains("textReverse"),
            () -> result.diagnostics().toString());
      }
    }
  }

  @Test
  void packagesJdkBindingAndRunsItsConsumerWithoutAJavaArtifact() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("producer/jdk/sql"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          module(name: "jdk.sql", version: 1, exports: ["DriverManager"],
            binding: jarBinding(target: jdkModule(name: "java.sql"), api: [
              jarType(name: "java.sql.DriverManager", members: ["getLoginTimeout"])
            ]))
        }
        """);
    Path repository = temporaryDirectory.resolve("repository");
    ModulePackager.PackagedModule packaged;
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (environment;
        var compiler = environment.compilerSession();
        var projects = environment.projectLoader()) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);
      packaged = new ModulePackager(projects, compiler).packageModule(modulePath, repository);
    }
    var restored = new ModuleArchiveReader().read(packaged.archive());
    assertTrue(restored.descriptor().binding().orElseThrow().target() instanceof JdkModuleTarget);
    assertFalse(Files.readString(packaged.pom()).contains("<type>jar</type>"));
    Path app = Files.createDirectories(temporaryDirectory.resolve("consumer/sample"));
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() {
          module(dependencies: [dependency(repository: "github", name: "jdk.sql", version: 1)])
        }
        """);
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package sample
        import jdk.sql.driverManagerGetLoginTimeout
        Void main() { printLine(driverManagerGetLoginTimeout()) }
        """);
    assertEquals(
        java.sql.DriverManager.getLoginTimeout() + System.lineSeparator(), run(repository, entry));
  }

  @Test
  void rejectsASecondOwnerForJavaBaseClassesBeforePublishingArtifacts() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("producer/jdk/base"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          module(name: "jdk.base", version: 1, exports: ["LocalDate"],
            binding: jarBinding(target: jdkModule(name: "java.base"), api: [
              jarType(name: "java.time.LocalDate", members: ["now"])
            ]))
        }
        """);
    Path repository = temporaryDirectory.resolve("repository");
    try (var environment = ProjectEnvironment.bootstrap(new NormRuntime());
        var compiler = environment.compilerSession();
        var projects = environment.projectLoader()) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);
      IOException failure =
          assertThrows(
              IOException.class,
              () -> new ModulePackager(projects, compiler).packageModule(modulePath, repository));
      assertTrue(
          failure.getMessage().contains("Java base type must be owned by java.base"),
          failure.getMessage());
      assertFalse(Files.exists(repository));
    }
  }

  @Test
  void runsTheDocumentedDependencyConsumerAgainstAPackagedLibrary() throws Exception {
    Path entry =
        Path.of(ModulePackagerTest.class.getResource("/docs/dependency/app/Main.norm").toURI());
    Path lesson = entry.getParent().getParent();
    Path repository = temporaryDirectory.resolve("repository");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var compiler = environment.compilerSession();
        ProjectLoader projects = environment.projectLoader()) {
      new ModulePackager(projects, compiler)
          .packageModule(lesson.resolve("producer/library/module.norm"), repository);
    }
    Path consumer = Files.createDirectories(temporaryDirectory.resolve("consumer/app"));
    Files.copy(lesson.resolve("app/module.norm"), consumer.resolve("module.norm"));
    Files.copy(entry, consumer.resolve("Main.norm"));
    assertEquals(
        Files.readString(lesson.resolve("expected.out")).replace("\r\n", "\n"),
        run(repository, consumer.resolve("Main.norm")).replace("\r\n", "\n"));
  }

  @Test
  void rejectsInvalidLibraryBodiesBeforePublishingAnyArtifacts() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("library/broken"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        "Module module() { return module(name: \"broken\", version: 1, exports: [\"Value\"]) }");
    Files.writeString(
        module.resolve("Value.norm"),
        "package broken public Integer value() { return missingFunction() }");
    Path repository = temporaryDirectory.resolve("repository");
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var compiler = environment.compilerSession();
        var projects = environment.projectLoader()) {
      var failure =
          assertThrows(
              ModuleCompilationException.class,
              () -> new ModulePackager(projects, compiler).packageModule(modulePath, repository));
      assertTrue(
          failure.diagnostics().stream()
              .anyMatch(value -> value.message().contains("missingFunction")));
      assertFalse(Files.exists(repository));
    }
  }

  @Test
  void preservesResultBuilderContractsAcrossNarDependencies() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("library/example/builders"));
    Path modulePath = module.resolve("module.norm");
    Path source = module.resolve("Words.norm");
    Files.writeString(
        modulePath,
        """
        Module module() { module(name: "example.builders", version: 1, exports: ["Words"]) }
        """);
    Files.writeString(
        source,
        """
        package example.builders
        import std.build.BuildWith
        import std.build.ResultBuilder
        public class Words implements ResultBuilder<String, String> {
          private String text = ""
          Void add(String value) { text = text + value }
          String finish() { text }
        }
        public String words(@BuildWith(Words.class) Function<String()> content) { content() }
        """);
    Path repository = temporaryDirectory.resolve("repository");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var compiler = environment.compilerSession();
        ProjectLoader projects = environment.projectLoader()) {
      new ModulePackager(projects, compiler).packageModule(modulePath, repository);
    }
    Files.delete(source);
    Path app = Files.createDirectories(temporaryDirectory.resolve("consumer/sample"));
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() { module(dependencies: [dependency(repository: "github", name: "example.builders", version: 1)]) }
        """);
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package sample
        import example.builders.words
        Void main() { printLine(words { "a" if true { "b" } }) }
        """);
    assertEquals("ab" + System.lineSeparator(), run(repository, entry));
  }

  @Test
  void preservesComputedPropertiesAndClosuresAcrossNarDependencies() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("library/example/state"));
    Path modulePath = module.resolve("module.norm");
    Path source = module.resolve("State.norm");
    Files.writeString(
        modulePath,
        "Module module() { return module(name: \"example.state\", version: 1, exports: [\"State\"])"
            + " }");
    Files.writeString(
        source,
        """
        package example.state
        public class State<T> {
          private T stored
          State(T initial) { stored = initial }
          T value { get { return stored } set(next) { stored = next } }
          Function<T()> reader() { return () { value } }
        }
        """);
    Path repository = temporaryDirectory.resolve("repository");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var compiler = environment.compilerSession();
        ProjectLoader projects = environment.projectLoader()) {
      new ModulePackager(projects, compiler).packageModule(modulePath, repository);
    }
    Files.delete(source);

    Path app = Files.createDirectories(temporaryDirectory.resolve("consumer/sample"));
    Files.writeString(
        app.resolve("module.norm"),
        "Module module() { return module(dependencies: [dependency(repository: \"github\", name:"
            + " \"example.state\", version: 1)]) }");
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package sample
        import example.state.State
        class Counter extends State<Integer> {
          Counter() { super(initial: 2) }
          Void increment() { value = value + 3 }
        }
        Void main() {
          var text = State<String>("before")
          var read = text.reader()
          text.value = "after"
          printLine(read())
          var counter = Counter()
          counter.increment()
          printLine(counter.value)
        }
        """);
    assertEquals(
        "after" + System.lineSeparator() + "5" + System.lineSeparator(), run(repository, entry));
  }

  @Test
  void rejectsPublishingALocalModuleWithoutADeclaredVersion() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("local/sample"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath, "Module module() { return module(dependencies: [], exports: [\"Main\"]) }");
    Files.writeString(module.resolve("Main.norm"), "package sample public Void main() {}");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    try (var compiler = environment.compilerSession();
        ProjectLoader projects = environment.projectLoader()) {
      IOException exception =
          assertThrows(
              IOException.class,
              () ->
                  new ModulePackager(projects, compiler)
                      .packageModule(modulePath, temporaryDirectory.resolve("repository")));

      assertTrue(exception.getMessage().contains("must declare a version"));
    }
  }

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
    String nativePath = "META-INF/native-image/example/assets/native-image.properties";
    String nativeOptions = "Args = -Dexample.assets.enabled=true\n";
    Path nativeResource = module.resolve("resources").resolve(nativePath);
    Files.createDirectories(nativeResource.getParent());
    Files.writeString(nativeResource, nativeOptions);
    Path repository = temporaryDirectory.resolve("repository");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    ModulePackager.PackagedModule packaged;
    try (var compiler = environment.compilerSession();
        ProjectLoader projects = environment.projectLoader()) {
      packaged = new ModulePackager(projects, compiler).packageModule(modulePath, repository);
    }
    try (ZipFile archive = new ZipFile(packaged.archive().toFile())) {
      var entry = archive.getEntry("resources/public/icon.bin");
      assertTrue(entry != null);
      assertTrue(java.util.Arrays.equals(icon, archive.getInputStream(entry).readAllBytes()));
      assertEquals(
          nativeOptions,
          new String(
              archive.getInputStream(archive.getEntry("resources/" + nativePath)).readAllBytes(),
              java.nio.charset.StandardCharsets.UTF_8));
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
            dependencies: [dependency(repository: "github", name: "example.assets", version: 1)]
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
    try (ApplicationRunner launcher =
            new ApplicationRunner(
                consumerEnvironment.projectLoader(MavenTestRepository.prepare(repository)),
                consumerEnvironment.compilerSession(),
                backend);
        var compilation = launcher.compileApplication(entry)) {
      assertEquals(
          nativeOptions,
          Files.readString(
              compilation.application().orElseThrow().annotations().classes().resolve(nativePath)));
      assertTrue(
          compilation.result().isSuccess(), () -> compilation.result().diagnostics().toString());
      org.junit.jupiter.api.Assertions.assertArrayEquals(
          icon,
          Files.readAllBytes(
              compilation
                  .application()
                  .orElseThrow()
                  .annotations()
                  .classes()
                  .resolve("public/icon.bin")));
    }
  }

  @Test
  void preservesGenericTypeDefaultsAcrossNarDependencies() throws Exception {
    Path module = Files.createDirectories(temporaryDirectory.resolve("library/example/outcome"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        "Module module() { return module(name: \"example.outcome\", version: 1, exports:"
            + " [\"Outcome\"]) }");
    Files.writeString(
        module.resolve("Outcome.norm"),
        "package example.outcome public enum Outcome<T, E = String> { Ok(T value), Err(E error) }");
    Path repository = temporaryDirectory.resolve("repository");
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var compiler = environment.compilerSession();
        ProjectLoader projects = environment.projectLoader()) {
      new ModulePackager(projects, compiler).packageModule(modulePath, repository);
    }

    Path app = Files.createDirectories(temporaryDirectory.resolve("consumer/sample"));
    Files.writeString(
        app.resolve("module.norm"),
        "Module module() { return module(dependencies: [dependency(repository: \"github\", name:"
            + " \"example.outcome\", version: 1)]) }");
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        entry,
        "package sample import example.outcome.Outcome Void main() { Outcome<Integer> result ="
            + " Outcome.Err(\"invalid\") }");

    assertEquals("", run(repository, entry));
  }

  @Test
  void packagesAuthoredSupportSourceInARuntimeAdapter() throws Exception {
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
                version: "%s"
              ),
              api: []
            )
          )
        }
        """
            .formatted(MavenTestRepository.commonsLang().version()));
    Files.writeString(
        module.resolve("Internal.norm"),
        """
        package empty.adapter
        class Internal {}
        """);
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var compiler = environment.compilerSession();
        ProjectLoader projects =
            environment.projectLoader(
                MavenTestRepository.prepare(temporaryDirectory.resolve("maven-cache")))) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);

      ModulePackager.PackagedModule packaged =
          new ModulePackager(projects, compiler)
              .packageModule(modulePath, temporaryDirectory.resolve("repository"));
      assertTrue(Files.isRegularFile(packaged.archive()));
      try (ZipFile archive = new ZipFile(packaged.archive().toFile())) {
        assertEquals(
            1, archive.stream().filter(entry -> entry.getName().startsWith("sources/")).count());
        assertTrue(archive.getEntry("sources/empty/adapter/Internal.norm") != null);
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
            exports: ["StringUtils"],
            binding: jarBinding(
              target: mavenJar(
                group: "org.apache.commons",
                artifact: "commons-lang3",
                version: "%s"
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
        """
            .formatted(MavenTestRepository.commonsLang().version()));
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());
    Path repository = temporaryDirectory.resolve("repository");

    ModulePackager.PackagedModule packaged;
    try (var compiler = environment.compilerSession();
        ProjectLoader projects =
            environment.projectLoader(
                MavenTestRepository.prepare(temporaryDirectory.resolve("maven-cache")))) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);
      packaged = new ModulePackager(projects, compiler).packageModule(modulePath, repository);
    }

    var archived = new ModuleArchiveReader().read(packaged.archive());
    try (var resolver =
        new dev.w0fv1.norm.jvm.JarResolver(temporaryDirectory.resolve("empty-java-cache"))) {
      var graph =
          resolver.resolvePublished(
              packaged.archive(), repository, archived.descriptor().binding().orElseThrow());
      assertTrue(graph.root().file().startsWith(temporaryDirectory.resolve("empty-java-cache")));
      assertEquals(archived.binding().orElseThrow().graphId(), graph.contentId());
      assertTrue(Files.isRegularFile(graph.root().file()));
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
      assertTrue(archive.getEntry("binding/prepared.bin") != null);
      assertTrue(archive.getEntry(dev.w0fv1.norm.frontend.CompiledModule.ENTRY) != null);
      assertEquals(
          dev.w0fv1.norm.frontend.CompiledModule.ABI,
          manifest.getAsJsonObject("core").get("abi").getAsString());
      assertEquals(
          dev.w0fv1.norm.jvm.PublishedJarBinding.ABI,
          manifest.getAsJsonObject("jar").get("bindingAbi").getAsString());
      assertEquals(64, manifest.getAsJsonObject("jar").get("bindingId").getAsString().length());
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
            dependencies: [dependency(repository: "github", name: "commons.lang", version: 1)]
          )
        }
        """);
    Path unavailableJarCache = temporaryDirectory.resolve("unavailable-jar-cache");
    Files.writeString(unavailableJarCache, "not a repository");
    ProjectEnvironment analysisEnvironment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (ProjectLoader projects =
        analysisEnvironment.projectLoader(repository, unavailableJarCache)) {
      ProjectSourceSet sourceSet = projects.loadForAnalysis(entry).sources();

      assertTrue(sourceSet.jarBindings().isEmpty());
      assertEquals(1, sourceSet.bindingSourceDocuments().size());
      assertTrue(
          sourceSet.sources().stream()
              .anyMatch(source -> source.displayName().endsWith("StringUtils.norm")));
    }
    StringWriter output = new StringWriter();
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects =
        consumerEnvironment.projectLoader(MavenTestRepository.prepare(repository))) {
      ProjectSourceSet runtimeSources = projects.load(entry).sources();
      ProjectSourceSet testSources = projects.loadForTests(entry).sources();
      assertEquals(1, testSources.jarBindings().size());
      assertEquals(runtimeSources.bindingSourceDocuments(), testSources.bindingSourceDocuments());
    }
    try (ApplicationRunner launcher =
        new ApplicationRunner(
            consumerEnvironment.projectLoader(MavenTestRepository.prepare(repository)),
            consumerEnvironment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      assertTrue(result.output().orElseThrow().state().buildReport().importedDefinitions() > 0);
    }
    assertEquals("mroN" + System.lineSeparator(), output.toString());
    for (String damage : java.util.List.of("payload", "abi", "core-payload", "core-abi")) {
      Path damaged = temporaryDirectory.resolve(damage + ".nar");
      try (var original = new ZipFile(packaged.archive().toFile());
          var rewritten = new java.util.zip.ZipOutputStream(Files.newOutputStream(damaged))) {
        for (var item : original.stream().toList()) {
          byte[] bytes;
          try (var input = original.getInputStream(item)) {
            bytes = input.readAllBytes();
          }
          if (damage.equals("payload") && item.getName().equals("binding/prepared.bin"))
            bytes[0] ^= 1;
          if (damage.equals("core-payload")
              && item.getName().equals(dev.w0fv1.norm.frontend.CompiledModule.ENTRY)) bytes[0] ^= 1;
          if (damage.equals("core-abi") && item.getName().equals("module.json"))
            bytes =
                new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    .replace(dev.w0fv1.norm.frontend.CompiledModule.ABI, "unknown-core-abi")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          if (damage.equals("abi") && item.getName().equals("module.json"))
            bytes =
                new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                    .replace(dev.w0fv1.norm.jvm.PublishedJarBinding.ABI, "unknown-binding-abi")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
          rewritten.putNextEntry(new java.util.zip.ZipEntry(item.getName()));
          rewritten.write(bytes);
          rewritten.closeEntry();
        }
      }
      assertThrows(IOException.class, () -> new ModuleArchiveReader().read(damaged));
    }
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
            exports: ["StringUtils"],
            binding: jarBinding(
              target: mavenJar(
                group: "org.apache.commons",
                artifact: "commons-lang3",
                version: "%s"
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
        """
            .formatted(MavenTestRepository.commonsLang().version()));
    Path bindingRepository = temporaryDirectory.resolve("binding-repository");
    ProjectEnvironment bindingEnvironment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var compiler = bindingEnvironment.compilerSession();
        ProjectLoader projects =
            bindingEnvironment.projectLoader(
                MavenTestRepository.prepare(temporaryDirectory.resolve("binding-cache")))) {
      new ModuleBindingResolutionService(projects).resolve(bindingModule);
      new ModulePackager(projects, compiler).packageModule(bindingModule, bindingRepository);
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
    try (var compiler = pureEnvironment.compilerSession();
        ProjectLoader projects = pureEnvironment.projectLoader()) {
      packaged = new ModulePackager(projects, compiler).packageModule(pureModule, pureRepository);
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
            dependencies: [dependency(repository: "github", name: "commons.lang", version: 1)]
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
    Path base =
        Files.createDirectories(temporaryDirectory.resolve("sources/dependencies/example/base"));
    Files.writeString(
        base.resolve("module.norm"),
        "Module module() { module(name: \"example.base\", version: 2, exports: [\"Base\"]) }");
    Files.writeString(base.resolve("Base.norm"), "package example.base\npublic class Base {}\n");
    Path module = Files.createDirectories(temporaryDirectory.resolve("sources/example/adapter"));
    Path modulePath = module.resolve("module.norm");
    Files.writeString(
        modulePath,
        """
        Module module() {
          return module(
            name: "example.adapter",
            version: 1,
            dependencies: [exportedDependency(repository: "github", name: "example.base", version: 2)],
            binding: jarBinding(
              target: mavenJar(
                group: "org.apache.commons",
                artifact: "commons-lang3",
                version: "%s"
              ),
              api: [jarType(name: "StringUtils", members: ["reverse"])]
            )
          )
        }
        """
            .formatted(MavenTestRepository.commonsLang().version()));
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new NormRuntime());

    ModulePackager.PackagedModule packaged;
    try (var compiler = environment.compilerSession();
        ProjectLoader projects =
            environment.projectLoader(
                MavenTestRepository.prepare(temporaryDirectory.resolve("maven-cache")))) {
      new ModuleBindingResolutionService(projects).resolve(modulePath);
      packaged =
          new ModulePackager(projects, compiler)
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
      assertEquals(
          ModuleArchiveFormat.FORMAT_VERSION,
          JsonParser.parseString(manifest).getAsJsonObject().get("formatVersion").getAsInt());
      assertFalse(ModuleArchiveFormat.isReadable(ModuleArchiveFormat.FORMAT_VERSION - 1));
      assertTrue(
          JsonParser.parseString(manifest)
              .getAsJsonObject()
              .getAsJsonObject("module")
              .getAsJsonArray("dependencies")
              .get(0)
              .getAsJsonObject()
              .get("repository")
              .getAsString()
              .equals("github"));
      assertTrue(
          JsonParser.parseString(manifest)
              .getAsJsonObject()
              .getAsJsonObject("module")
              .getAsJsonArray("dependencies")
              .get(0)
              .getAsJsonObject()
              .get("exported")
              .getAsBoolean());
      assertTrue(Files.isRegularFile(packaged.archiveDigest()));
      assertTrue(Files.isRegularFile(packaged.pomDigest()));
    }
  }

  private static String run(Path repository, Path entry) throws Exception {
    StringWriter output = new StringWriter();
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ApplicationRunner launcher =
        new ApplicationRunner(
            environment.projectLoader(MavenTestRepository.prepare(repository)),
            environment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      assertTrue(result.output().orElseThrow().state().buildReport().importedDefinitions() > 0);
    }
    return output.toString();
  }
}
