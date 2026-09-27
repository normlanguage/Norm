package dev.w0fv1.norm.gradle;

import dev.w0fv1.norm.codegen.BuildMetadataGenerator;
import dev.w0fv1.norm.codegen.BuiltinAbiGenerator;
import dev.w0fv1.norm.packaging.RuntimeImageGenerator;
import dev.w0fv1.norm.packaging.RuntimeLauncherGenerator;
import dev.w0fv1.norm.packaging.RuntimeModuleAssembler;
import dev.w0fv1.norm.packaging.RuntimeStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.jvm.tasks.ProcessResources;

public final class NormCompilerPlugin implements Plugin<Project> {
  public abstract static class GenerateAbi extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getSchema();

    @OutputDirectory
    public abstract DirectoryProperty getDestination();

    @TaskAction
    public void generate() throws IOException {
      BuiltinAbiGenerator.generate(
          getSchema().get().getAsFile().toPath(), getDestination().get().getAsFile().toPath());
    }
  }

  public abstract static class GenerateMetadata extends DefaultTask {
    @Input
    public abstract Property<String> getNormVersion();

    @Input
    public abstract Property<String> getGraalVersion();

    @OutputDirectory
    public abstract DirectoryProperty getDestination();

    @TaskAction
    public void generate() throws IOException {
      BuildMetadataGenerator.generate(
          getNormVersion().get(),
          getGraalVersion().get(),
          getDestination().get().getAsFile().toPath());
    }
  }

  public abstract static class AssembleRuntime extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getCompilerJar();

    @Classpath
    public abstract ConfigurableFileCollection getRuntimeClasspath();

    @InputDirectory
    public abstract DirectoryProperty getJavaHome();

    @Input
    public abstract Property<String> getStorage();

    @OutputDirectory
    public abstract DirectoryProperty getDestination();

    @TaskAction
    public void assemble() throws IOException {
      Path output = getDestination().get().getAsFile().toPath();
      List<Path> libraries =
          getRuntimeClasspath().getFiles().stream().map(java.io.File::toPath).toList();
      RuntimeModuleAssembler.assemble(
          getCompilerJar().get().getAsFile().toPath(),
          libraries,
          output,
          RuntimeStorage.parse(getStorage().get()));
      RuntimeImageGenerator.generate(
          getJavaHome().get().getAsFile().toPath(), output.resolve("runtime"));
      RuntimeLauncherGenerator.generate(
          output.resolve("bin"),
          RuntimeLauncherGenerator.JavaRuntime.BUNDLED,
          "dev.w0fv1.norm",
          "dev.w0fv1.norm.cli.Main",
          List.of(
              "--add-modules=java.se",
              "--sun-misc-unsafe-memory-access=allow",
              "--enable-native-access=org.graalvm.truffle"));
      Files.copy(
          getProject().getRootProject().file("LICENSE").toPath(),
          output.resolve("LICENSE"),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      Files.copy(
          getProject().getRootProject().file("LICENSING.md").toPath(),
          output.resolve("LICENSING.md"),
          java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
  }

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply("java");
    var javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
    javaExtension.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(25));
    var sets = project.getExtensions().getByType(SourceSetContainer.class);
    var main = sets.getByName("main");
    var test = sets.getByName("test");
    var abi =
        project
            .getTasks()
            .register(
                "generateBuiltinAbi",
                GenerateAbi.class,
                task -> {
                  task.getSchema()
                      .set(project.getLayout().getProjectDirectory().file("stdlib-abi.json"));
                  task.getDestination()
                      .set(project.getLayout().getBuildDirectory().dir("generated/sources/norm"));
                });
    var metadata =
        project
            .getTasks()
            .register(
                "generateBuildMetadata",
                GenerateMetadata.class,
                task -> {
                  task.getNormVersion()
                      .set(project.provider(() -> project.getVersion().toString()));
                  task.getGraalVersion()
                      .set(
                          project
                              .getExtensions()
                              .getByType(VersionCatalogsExtension.class)
                              .named("libs")
                              .findVersion("graalvm")
                              .orElseThrow()
                              .getRequiredVersion());
                  task.getDestination()
                      .set(
                          project
                              .getLayout()
                              .getBuildDirectory()
                              .dir("generated/sources/metadata"));
                });
    main.getJava().srcDir(abi.flatMap(GenerateAbi::getDestination));
    main.getJava().srcDir(metadata.flatMap(GenerateMetadata::getDestination));
    main.getResources().srcDir(project.getRootProject().file("norm/stdlib"));
    main.getResources().exclude("std/tests/**");
    test.getResources().srcDir(project.getRootProject().file("norm/tests"));
    project
        .getTasks()
        .named(
            "compileJava",
            JavaCompile.class,
            task -> {
              task.dependsOn(abi, metadata);
              task.getModularity().getInferModulePath().set(true);
              task.getOptions().getRelease().set(25);
            });
    project
        .getTasks()
        .named(
            "compileTestJava",
            JavaCompile.class,
            task ->
                task.getOptions()
                    .getCompilerArgs()
                    .addAll(
                        List.of(
                            "--add-modules", "jdk.httpserver,java.compiler",
                            "--add-reads", "dev.w0fv1.norm=jdk.httpserver",
                            "--add-reads", "dev.w0fv1.norm=java.compiler")));
    var nativeExecution = project.getConfigurations().create("nativeExecution");
    var nativeExecutionRuntime = project.getConfigurations().create("nativeExecutionRuntime");
    var nativeHosted = project.getConfigurations().create("nativeHosted");
    project
        .getConfigurations()
        .getByName("implementation")
        .extendsFrom(nativeExecution, nativeHosted);
    project.getConfigurations().getByName("runtimeOnly").extendsFrom(nativeExecutionRuntime);
    dependencies(project);
    var rawArtifacts =
        project
            .getConfigurations()
            .getByName("runtimeClasspath")
            .getIncoming()
            .artifactView(
                view ->
                    view.attributes(
                        attributes ->
                            attributes.attribute(Attribute.of("javaModule", Boolean.class), false)))
            .getArtifacts();
    var catalog =
        project
            .getTasks()
            .register(
                "generateToolchainCatalog",
                GenerateToolchainCatalog.class,
                task -> {
                  task.getResolvedArtifacts().from(rawArtifacts.getArtifactFiles());
                  task.getResolutionIdentity()
                      .set(
                          project.provider(
                              () ->
                                  GenerateToolchainCatalog.resolvedGraph(
                                          project.getConfigurations().getByName("runtimeClasspath"))
                                      .toString()));
                  task.getPurposeIdentity()
                      .set(
                          project.provider(
                              () ->
                                  List.of(
                                          "nativeExecution",
                                          "nativeExecutionRuntime",
                                          "nativeHosted")
                                      .stream()
                                      .map(
                                          name ->
                                              name
                                                  + ":"
                                                  + project
                                                      .getConfigurations()
                                                      .getByName(name)
                                                      .getDependencies()
                                                      .stream()
                                                      .map(
                                                          dependency ->
                                                              dependency.getGroup()
                                                                  + ":"
                                                                  + dependency.getName()
                                                                  + ":"
                                                                  + dependency.getVersion())
                                                      .sorted()
                                                      .toList())
                                      .toList()
                                      .toString()));
                  task.getStorage()
                      .set(
                          project
                              .getProviders()
                              .gradleProperty("normRuntimeStorage")
                              .orElse("sealed"));
                  task.getStaging()
                      .set(project.getLayout().getBuildDirectory().dir("toolchain-runtime"));
                  task.getDestination()
                      .set(
                          project
                              .getLayout()
                              .getBuildDirectory()
                              .file("generated/resources/toolchain/toolchain-artifacts.json"));
                });
    main.getResources()
        .srcDir(project.getLayout().getBuildDirectory().dir("generated/resources/toolchain"));
    var reachability =
        project
            .getTasks()
            .register(
                "prepareReachabilityMetadata",
                PrepareReachability.class,
                task -> {
                  task.getOffline().set(project.getGradle().getStartParameter().isOffline());
                  String local =
                      project.getProviders().gradleProperty("normReachabilityMetadata").getOrNull();
                  if (local != null)
                    task.getLocalArchive().set(project.getRootProject().file(local));
                  task.getDestination()
                      .set(
                          project
                              .getLayout()
                              .getBuildDirectory()
                              .file(
                                  "generated/resources/reachability-metadata/graalvm-reachability-metadata.zip"));
                });
    main.getResources()
        .srcDir(
            project
                .getLayout()
                .getBuildDirectory()
                .dir("generated/resources/reachability-metadata"));
    project.getTasks().named("processResources", task -> task.dependsOn(catalog, reachability));
    if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
      var nativeHostOutput =
          project.getLayout().getBuildDirectory().dir("generated/resources/native-host");
      var host =
          project
              .getTasks()
              .register(
                  "publishNativeHost",
                  Exec.class,
                  task -> {
                    var source =
                        project
                            .getRootProject()
                            .file("cli/launcher/Norm.NativeHost/Norm.NativeHost.csproj");
                    var artifacts =
                        project
                            .getRootProject()
                            .getLayout()
                            .getBuildDirectory()
                            .dir("dotnet/native-host")
                            .get()
                            .getAsFile();
                    task.getInputs()
                        .files(
                            project.fileTree(
                                project.getRootProject().file("cli/launcher/Norm.NativeHost")),
                            project.fileTree(
                                project.getRootProject().file("cli/launcher/Norm.Launcher")));
                    task.getOutputs().dir(nativeHostOutput);
                    task.commandLine(
                        "dotnet",
                        "publish",
                        source.getAbsolutePath(),
                        "-c",
                        "Release",
                        "-r",
                        "win-x64",
                        "--self-contained",
                        "true",
                        "-o",
                        nativeHostOutput.get().getAsFile().getAbsolutePath(),
                        "--artifacts-path",
                        artifacts.getAbsolutePath());
                  });
      project
          .getTasks()
          .named(
              "processResources",
              ProcessResources.class,
              task -> {
                task.dependsOn(host);
                task.from(nativeHostOutput, spec -> spec.include("native-host.exe"));
              });
    }
    var jar =
        project
            .getTasks()
            .named(
                "jar",
                Jar.class,
                task -> {
                  task.from(
                      project.getRootProject().file("LICENSE"), spec -> spec.into("META-INF"));
                  task.from(
                      project.getRootProject().file("LICENSING.md"), spec -> spec.into("META-INF"));
                  task.getManifest()
                      .attributes(
                          java.util.Map.of(
                              "Implementation-Version",
                              project.getVersion().toString(),
                              "Main-Class",
                              "dev.w0fv1.norm.cli.Main"));
                });
    var testRuntime =
        project
            .getTasks()
            .register(
                "assembleTestRuntime",
                AssembleTestRuntime.class,
                task -> {
                  task.getRuntimeClasspath().from(rawArtifacts.getArtifactFiles());
                  task.getStorage()
                      .set(
                          project
                              .getProviders()
                              .gradleProperty("normRuntimeStorage")
                              .orElse("sealed"));
                  task.getDestination()
                      .set(project.getLayout().getBuildDirectory().dir("test-runtime"));
                });
    project
        .getTasks()
        .named(
            "test",
            Test.class,
            task -> {
              task.useJUnitPlatform();
              task.dependsOn(testRuntime, jar);
              task.getInputs().dir(project.getRootProject().file("norm/libraries"));
              task.systemProperty(
                  "norm.test.abi", project.file("stdlib-abi.json").getAbsolutePath());
              task.systemProperty(
                  "norm.test.stdlib",
                  project.getRootProject().file("norm/stdlib/std").getAbsolutePath());
              task.systemProperty(
                  "norm.test.runtimeDirectory",
                  testRuntime
                      .flatMap(AssembleTestRuntime::getDestination)
                      .get()
                      .getAsFile()
                      .getAbsolutePath());
              task.systemProperty(
                  "norm.test.modulePath",
                  main.getOutput().getClassesDirs().getAsPath()
                      + java.io.File.pathSeparator
                      + testRuntime
                          .flatMap(AssembleTestRuntime::getDestination)
                          .get()
                          .getAsFile()
                          .toPath()
                          .resolve("lib"));
              String repository =
                  project.getProviders().gradleProperty("normTestMavenRepository").getOrNull();
              if (repository != null) {
                var fixture = project.getRootProject().file(repository);
                task.getInputs().dir(fixture);
                task.systemProperty("norm.test.mavenRepository", fixture.getAbsolutePath());
              }
            });
    var toolchains = project.getExtensions().getByType(JavaToolchainService.class);
    var runtime =
        project
            .getTasks()
            .register(
                "installRuntimeDist",
                AssembleRuntime.class,
                task -> {
                  task.dependsOn(jar);
                  task.getCompilerJar().set(jar.flatMap(Jar::getArchiveFile));
                  task.getRuntimeClasspath().from(rawArtifacts.getArtifactFiles());
                  task.getJavaHome()
                      .set(
                          toolchains
                              .launcherFor(
                                  spec -> spec.getLanguageVersion().set(JavaLanguageVersion.of(25)))
                              .map(launcher -> launcher.getMetadata().getInstallationPath()));
                  task.getStorage()
                      .set(
                          project
                              .getProviders()
                              .gradleProperty("normRuntimeStorage")
                              .orElse("sealed"));
                  task.getDestination()
                      .set(project.getLayout().getBuildDirectory().dir("norm-runtime"));
                });
    var releaseTargets = project.getLayout().getProjectDirectory().file("release-targets.json");
    ReleaseTarget target;
    try {
      target =
          ReleaseTarget.select(
              releaseTargets.getAsFile().toPath(),
              System.getProperty("os.name"),
              System.getProperty("os.arch"));
    } catch (IOException exception) {
      throw new org.gradle.api.GradleException("Cannot select Norm release target", exception);
    }
    if (target.standalone()) {
      project
          .getTasks()
          .register(
              "packageDistribution",
              PackageDistribution.class,
              task -> {
                task.dependsOn(runtime);
                task.getInputs().file(releaseTargets);
                task.getRuntimeDirectory().set(runtime.flatMap(AssembleRuntime::getDestination));
                task.getNormVersion().set(project.provider(() -> project.getVersion().toString()));
                task.getAssetFile()
                    .set(
                        project
                            .getRootProject()
                            .getLayout()
                            .getBuildDirectory()
                            .file(
                                "distributions/" + target.asset(project.getVersion().toString())));
                task.getLauncherProject()
                    .set(
                        project
                            .getRootProject()
                            .getLayout()
                            .getProjectDirectory()
                            .file("cli/launcher/Norm.Launcher/Norm.Launcher.csproj"));
                task.getLauncherSourceDirectory()
                    .set(
                        project
                            .getRootProject()
                            .getLayout()
                            .getProjectDirectory()
                            .dir("cli/launcher/Norm.Launcher"));
                task.getLauncherIcon()
                    .set(
                        project
                            .getRootProject()
                            .getLayout()
                            .getProjectDirectory()
                            .file("docs/public/brand/norm.ico"));
                task.getLauncherWorkDirectory()
                    .set(project.getLayout().getBuildDirectory().dir("launcher"));
                task.getDotnetArtifactsDirectory()
                    .set(
                        project
                            .getRootProject()
                            .getLayout()
                            .getBuildDirectory()
                            .dir("dotnet/launcher"));
              });
    } else {
      project
          .getTasks()
          .register(
              "packageDistribution",
              PackageUnixDistribution.class,
              task -> {
                task.dependsOn(runtime);
                task.getRuntimeDirectory().set(runtime.flatMap(AssembleRuntime::getDestination));
                task.getStagingDirectory()
                    .set(project.getLayout().getBuildDirectory().dir("archive-stage"));
                task.getArchive()
                    .set(
                        project
                            .getRootProject()
                            .getLayout()
                            .getBuildDirectory()
                            .file(
                                "distributions/" + target.asset(project.getVersion().toString())));
                task.getInputs().file(releaseTargets);
              });
    }
  }

  private static void dependencies(Project project) {
    DependencyHandler dependencies = project.getDependencies();
    var libs = project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs");
    List<String> implementation =
        List.of(
            "lsp4j",
            "gson",
            "commonmark",
            "resolver-supplier",
            "codec",
            "compress",
            "jcl-slf4j",
            "junit-platform-launcher",
            "junit-platform-engine",
            "apiguardian",
            "reachability",
            "jspecify");
    implementation.forEach(
        value -> dependencies.add("implementation", libs.findLibrary(value).orElseThrow().get()));
    List.of("jackson-core", "jackson-yaml", "woodstox", "truffle-api", "polyglot", "objenesis")
        .forEach(
            value ->
                dependencies.add("nativeExecution", libs.findLibrary(value).orElseThrow().get()));
    List.of("nativeimage", "asm", "kryo")
        .forEach(
            value -> dependencies.add("nativeHosted", libs.findLibrary(value).orElseThrow().get()));
    dependencies.add("runtimeOnly", libs.findLibrary("truffle-runtime").orElseThrow().get());
    dependencies.add("runtimeOnly", libs.findLibrary("junit-jupiter-engine").orElseThrow().get());
    dependencies.add(
        "nativeExecutionRuntime", libs.findLibrary("slf4j-simple").orElseThrow().get());
    dependencies.add(
        "annotationProcessor", libs.findLibrary("truffle-processor").orElseThrow().get());
    List.of("junit-jupiter", "archunit", "websocket")
        .forEach(
            value ->
                dependencies.add(
                    "testImplementation", libs.findLibrary(value).orElseThrow().get()));
  }
}
