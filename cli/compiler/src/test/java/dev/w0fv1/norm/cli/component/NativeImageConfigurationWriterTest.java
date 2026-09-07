package dev.w0fv1.norm.cli.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import dev.w0fv1.norm.application.ApplicationInput;
import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.application.CompiledApplication;
import dev.w0fv1.norm.application.TemporaryDirectory;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.jvm.GeneratedJarBinding;
import dev.w0fv1.norm.jvm.JarApiSchema;
import dev.w0fv1.norm.jvm.JarBindingClasspath;
import dev.w0fv1.norm.jvm.JavaAnnotationProcessingOutput;
import dev.w0fv1.norm.jvm.JavaAnnotationStub;
import dev.w0fv1.norm.jvm.JavaApplicationMethodIndex;
import dev.w0fv1.norm.jvm.JavaBindingCallable;
import dev.w0fv1.norm.jvm.JavaCallableKind;
import dev.w0fv1.norm.jvm.JavaPrimitiveType;
import dev.w0fv1.norm.jvm.LinkedJarBinding;
import dev.w0fv1.norm.jvm.MavenJarIdentity;
import dev.w0fv1.norm.jvm.ResolvedJarArtifact;
import dev.w0fv1.norm.jvm.ResolvedJarBinding;
import dev.w0fv1.norm.jvm.ResolvedJarGraph;
import dev.w0fv1.norm.project.ModuleResource;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.project.ProjectResources;
import dev.w0fv1.norm.project.ProjectSourceSet;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class NativeImageConfigurationWriterTest {
  @TempDir Path directory;

  @Test
  void allocatesOnlyGeneratedApplicationObjectsWithoutConstructors() throws Exception {
    Path source = directory.resolve("main.norm");
    Files.writeString(
        source,
        """
        import std.annotation.RuntimeRetention
        import std.annotation.TypeTarget
        class Entity { String name }
        value Snapshot { String name }
        class NotGenerated {}
        interface Contract {}
        enum Choice { First, Second }
        annotation Label implements TypeTarget, RuntimeRetention { String text }
        Void main() {}
        """);
    try (var launcher = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()));
        var compiled = launcher.compileApplication(source)) {
      org.junit.jupiter.api.Assertions.assertTrue(
          compiled.result().isSuccess(), compiled.result().diagnostics().toString());
      var names = List.of("Entity", "Snapshot", "Contract", "Choice", "Label", "SyntheticHolder");
      var stubs =
          names.stream()
              .map(name -> new JavaAnnotationStub("norm.generated.application." + name, ""))
              .toList();
      var application =
          new CompiledApplication(
              new ApplicationInput(
                  compiled.application().orElseThrow().sourceSet().compilationRequest(),
                  java.util.Optional.of(compiled.application().orElseThrow().sourceSet())),
              compiled.result(),
              new JavaAnnotationProcessingOutput(
                  directory,
                  stubs,
                  new JavaApplicationMethodIndex.Analysis(java.util.Map.of(), java.util.Set.of())),
              compiled.application().orElseThrow().javaClasspath(),
              new TemporaryDirectory());
      var output =
          new NativeImageConfigurationWriter()
              .write(application, List.of(), directory.resolve("metadata"));
      var metadata =
          JsonParser.parseString(
                  Files.readString(
                      output
                          .classpath()
                          .resolve(
                              "META-INF/native-image/norm/application/reachability-metadata.json")))
              .getAsJsonObject();
      var allocated =
          metadata.getAsJsonArray("reflection").asList().stream()
              .map(value -> value.getAsJsonObject())
              .filter(
                  value ->
                      value.has("unsafeAllocated") && value.get("unsafeAllocated").getAsBoolean())
              .map(value -> value.get("type").getAsString())
              .sorted()
              .toList();
      var orderedTypes =
          metadata.getAsJsonArray("reflection").asList().stream()
              .map(value -> value.getAsJsonObject().get("type").getAsString())
              .toList();
      assertEquals(orderedTypes.stream().sorted().toList(), orderedTypes);
      assertEquals(
          List.of("norm.generated.application.Entity", "norm.generated.application.Snapshot"),
          allocated);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "sample.Controller,sample.package-info",
    "sample.web.Outer$Inner,sample.web.package-info",
    "Controller,''"
  })
  void preservesApplicationReflectionAndPackageQueries(String binaryName, String packageInfo)
      throws Exception {
    Path source = directory.resolve("main.norm");
    Files.writeString(source, "Void main() {}\n");
    try (var launcher = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()));
        var compilation = launcher.compileApplication(source)) {
      var application =
          new CompiledApplication(
              new ApplicationInput(
                  compilation.application().orElseThrow().sourceSet().compilationRequest(),
                  java.util.Optional.of(compilation.application().orElseThrow().sourceSet())),
              compilation.result(),
              new JavaAnnotationProcessingOutput(
                  directory,
                  List.of(
                      new JavaAnnotationStub(binaryName, ""),
                      new JavaAnnotationStub(binaryName + "Sibling", "")),
                  new JavaApplicationMethodIndex.Analysis(java.util.Map.of(), java.util.Set.of())),
              compilation.application().orElseThrow().javaClasspath(),
              new TemporaryDirectory());
      var output =
          new NativeImageConfigurationWriter()
              .write(application, List.of(), directory.resolve("metadata"));
      var metadata =
          JsonParser.parseString(
                  Files.readString(
                      output
                          .classpath()
                          .resolve(
                              "META-INF/native-image/norm/application/reachability-metadata.json")))
              .getAsJsonObject();
      var type = metadata.getAsJsonArray("reflection").get(0).getAsJsonObject();
      assertEquals(binaryName, type.get("type").getAsString());
      assertEquals(true, type.get("allDeclaredFields").getAsBoolean());
      assertEquals(true, type.get("allDeclaredConstructors").getAsBoolean());
      assertEquals(true, type.get("allDeclaredMethods").getAsBoolean());
      var queries =
          metadata.getAsJsonArray("reflection").asList().stream()
              .map(value -> value.getAsJsonObject())
              .filter(value -> value.get("type").getAsString().endsWith(".package-info"))
              .toList();
      assertEquals(packageInfo.isEmpty() ? 0 : 1, queries.size());
      if (!packageInfo.isEmpty()) {
        assertEquals(packageInfo, queries.getFirst().get("type").getAsString());
        assertEquals(1, queries.getFirst().size());
      }
    }
  }

  @ParameterizedTest
  @CsvSource({
    "public/index.html,public/index.html",
    "public/literal*.txt,public/literal\\*.txt",
    "public/**/template.txt,public/\\*\\*/template.txt"
  })
  void writesDistinctEnumTypesAndExactApplicationResources(String path, String glob)
      throws Exception {
    Path source = directory.resolve("main.norm");
    Files.writeString(source, "Void main() {}\n");
    Path jar = directory.resolve("sample.jar");
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new java.util.jar.JarEntry("META-INF/services/sample.Service"));
      output.write("sample.Provider\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      output.closeEntry();
      output.finish();
    }
    var artifact =
        new ResolvedJarArtifact(
            new MavenJarIdentity(new MavenArtifactCoordinate("sample", "library", "1")),
            jar,
            Sha256Digest.compute(jar));
    var type =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("sample.library", 1), "sample", "Mode");
    var generated =
        new GeneratedJarBinding(
            List.of(),
            List.of(),
            Map.of(),
            Map.of(type, "Lsample/Mode;"),
            Map.of(type, Map.of("First", "FIRST")),
            Map.of());
    var binding =
        new ResolvedJarBinding(
            new ResolvedJarGraph(artifact, List.of(artifact), List.of()),
            new JarApiSchema(List.of()),
            generated);
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    try (var launcher = ApplicationRunner.open(environment);
        var compilation = launcher.compileApplication(source)) {
      var original = compilation.application().orElseThrow().sourceSet();
      var sources =
          new ProjectSourceSet(
              original.root(),
              original.primaryPath(),
              original.rootModulePath(),
              original.modulePaths(),
              original.moduleDescriptors(),
              original.moduleArchives(),
              original.scope(),
              original.sources(),
              original.exportedSourcePaths(),
              original.bindingSourceDocuments(),
              List.of(binding),
              new ProjectResources(
                  Map.of(
                      original.scope().coordinate(original.primarySource().id()).module(),
                      Map.of(path, new ModuleResource(path, new byte[0])))),
              original.applicationFactory(),
              original.mainEntrypoint());
      var output =
          new NativeImageConfigurationWriter()
              .write(
                  new CompiledApplication(
                      new ApplicationInput(
                          sources.compilationRequest(), java.util.Optional.of(sources)),
                      compilation.result(),
                      compilation.application().orElseThrow().annotations(),
                      JarBindingClasspath.prepare(sources.jarBindings()),
                      new TemporaryDirectory()),
                  List.of(LinkedJarBinding.from(binding)),
                  directory.resolve("metadata"));
      var metadata =
          JsonParser.parseString(
                  Files.readString(
                      output
                          .classpath()
                          .resolve(
                              "META-INF/native-image/norm/application/reachability-metadata.json")))
              .getAsJsonObject();
      var reflection = metadata.getAsJsonArray("reflection");
      assertEquals(1, reflection.size());
      assertEquals("sample.Mode", reflection.get(0).getAsJsonObject().get("type").getAsString());
      assertEquals(
          List.of(glob),
          metadata.getAsJsonArray("resources").asList().stream()
              .map(value -> value.getAsJsonObject().get("glob").getAsString())
              .toList());
    }
  }

  @Test
  void directCallsDoNotRequireReflectionRegistration() throws Exception {
    var used =
        new JavaBindingCallable(
            "java.lang.System",
            "nanoTime",
            "()J",
            JavaCallableKind.STATIC_METHOD,
            List.of(),
            JavaPrimitiveType.LONG);
    var unused =
        new JavaBindingCallable(
            "java.lang.System",
            "gc",
            "()V",
            JavaCallableKind.STATIC_METHOD,
            List.of(),
            JavaPrimitiveType.VOID);
    var binding =
        new LinkedJarBinding(Map.of("used", used, "unused", unused), Map.of(), Map.of())
            .retainCalls(java.util.Set.of("used"));
    Path source = directory.resolve("main.norm");
    Files.writeString(source, "Void main() {}\n");
    try (var launcher = ApplicationRunner.open(ProjectEnvironment.bootstrap(new NormRuntime()));
        var compilation = launcher.compileApplication(source)) {
      var output =
          new NativeImageConfigurationWriter()
              .write(
                  compilation.application().orElseThrow(),
                  List.of(binding),
                  directory.resolve("metadata"));
      var reflection =
          JsonParser.parseString(
                  Files.readString(
                      output
                          .classpath()
                          .resolve(
                              "META-INF/native-image/norm/application/reachability-metadata.json")))
              .getAsJsonObject()
              .getAsJsonArray("reflection");
      assertEquals(0, reflection.size());
    }
  }

  @Test
  void convertsJvmDescriptorsToReachabilityMetadataNames() {
    assertEquals(
        "io.sample.http.annotation.Error",
        NativeImageConfigurationWriter.descriptorName("Lio/sample/http/annotation/Error;"));
    assertEquals(
        "java.lang.String[]", NativeImageConfigurationWriter.descriptorName("[Ljava/lang/String;"));
  }
}
