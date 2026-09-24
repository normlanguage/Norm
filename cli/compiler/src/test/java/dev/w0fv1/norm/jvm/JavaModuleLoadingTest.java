package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class JavaModuleLoadingTest {
  @TempDir Path directory;

  @Test
  void selectsForcedMultiReleaseModulesAndTheirDescriptorDependenciesWithoutUserRoots()
      throws Exception {
    Path required = writeModuleJar("sample.required", List.of(), false, Map.of());
    Path root =
        writeModuleJar(
            "sample.root",
            List.of("sample.required"),
            true,
            Map.of(
                "META-INF/native-image/sample/root/native-image.properties",
                "ForceOnModulePath = sample.\\\nroot\n"));
    Path unused = writeModuleJar("sample.unused", List.of(), false, Map.of());

    var selected = JavaModulePath.nativeImage(List.of(root, required, unused), List.of());

    assertEquals(List.of("sample.required", "sample.root"), selected.names());
    assertEquals(List.of(required, root), selected.paths());
  }

  @Test
  void keepsUserModuleRootsAndLeavesUnforcedModulesOnTheClasspath() throws Exception {
    Path user = writeModuleJar("sample.user", List.of(), false, Map.of());
    Path forced =
        writeModuleJar(
            "sample.forced",
            List.of(),
            false,
            Map.of(
                "META-INF/native-image/sample/forced/native-image.properties",
                "ForceOnModulePath=sample.forced\n"));
    Path unused = writeModuleJar("sample.unused", List.of(), false, Map.of());

    var selected =
        JavaModulePath.nativeImage(List.of(user, forced, unused), List.of("sample.user"));

    assertEquals(List.of("sample.forced", "sample.user"), selected.names());
    assertEquals(List.of(forced, user), selected.paths());
    assertTrue(JavaModulePath.nativeImage(List.of(unused), List.of()).paths().isEmpty());
  }

  @Test
  void validatesEveryForceOnModulePathResourceAgainstItsOwnJar() throws Exception {
    Path jar =
        writeModuleJar(
            "sample.root",
            List.of(),
            false,
            Map.of(
                "META-INF/native-image/sample/a/native-image.properties",
                "ForceOnModulePath=sample.root\n",
                "META-INF/native-image/sample/b/native-image.properties",
                "ForceOnModulePath=sample.other\n"));
    Path other = writeModuleJar("sample.other", List.of(), false, Map.of());

    var failure =
        assertThrows(
            java.lang.module.FindException.class,
            () -> JavaModulePath.nativeImage(List.of(jar, other), List.of()));

    assertTrue(failure.getMessage().contains("sample.other"));
    assertTrue(failure.getMessage().contains(jar.toString()));
  }

  @Test
  void movesOnlyAutomaticJarsExplicitlyForcedByNativeImageMetadata() throws Exception {
    Path forced = directory.resolve("sample.forced.automatic.jar");
    Path ordinary = directory.resolve("sample.ordinary.automatic.jar");
    for (Path path : List.of(forced, ordinary)) {
      var manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      manifest
          .getMainAttributes()
          .putValue(
              "Automatic-Module-Name",
              path.equals(forced) ? "sample.forced.automatic" : "sample.ordinary.automatic");
      try (var output = new JarOutputStream(Files.newOutputStream(path), manifest)) {
        output.putNextEntry(new JarEntry("sample/data.txt"));
        output.write(1);
        output.closeEntry();
        if (path.equals(forced)) {
          output.putNextEntry(
              new JarEntry("META-INF/native-image/sample/automatic/native-image.properties"));
          output.write(
              "ForceOnModulePath=sample.forced.automatic\n"
                  .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
          output.closeEntry();
        }
      }
    }

    var selected = JavaModulePath.nativeImage(List.of(forced, ordinary), List.of());

    assertEquals(List.of("sample.forced.automatic"), selected.names());
    assertEquals(List.of(forced), selected.paths());
    assertTrue(JavaModulePath.select(List.of(forced, ordinary), List.of()).paths().isEmpty());
  }

  private Path writeModuleJar(
      String name, List<String> dependencies, boolean multiRelease, Map<String, String> resources)
      throws Exception {
    var descriptor = new ClassWriter(0);
    descriptor.visit(Opcodes.V21, Opcodes.ACC_MODULE, "module-info", null, null, null);
    var module = descriptor.visitModule(name, 0, null);
    module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
    dependencies.forEach(dependency -> module.visitRequire(dependency, 0, null));
    module.visitEnd();
    descriptor.visitEnd();
    Path path = directory.resolve(name + ".jar");
    var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    if (multiRelease) manifest.getMainAttributes().putValue("Multi-Release", "true");
    try (var output = new JarOutputStream(Files.newOutputStream(path), manifest)) {
      output.putNextEntry(
          new JarEntry(
              multiRelease ? "META-INF/versions/21/module-info.class" : "module-info.class"));
      output.write(descriptor.toByteArray());
      output.closeEntry();
      for (var entry : resources.entrySet()) {
        output.putNextEntry(new JarEntry(entry.getKey()));
        output.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        output.closeEntry();
      }
    }
    return path;
  }

  @Test
  void resolvesOnlyTheModuleClosureOfDeclaredJarRoots() throws Exception {
    var artifacts = new java.util.ArrayList<ResolvedJarArtifact>();
    for (String name : List.of("root", "required", "optional")) {
      var descriptor = new ClassWriter(0);
      descriptor.visit(Opcodes.V17, Opcodes.ACC_MODULE, "module-info", null, null, null);
      var module = descriptor.visitModule("sample." + name, 0, null);
      module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
      if (name.equals("root")) module.visitRequire("sample.required", 0, null);
      if (name.equals("optional")) module.visitRequire("sample.missing", 0, null);
      module.visitExport("sample/" + name, 0);
      module.visitEnd();
      descriptor.visitEnd();
      var type = new ClassWriter(0);
      type.visit(
          Opcodes.V17,
          Opcodes.ACC_PUBLIC,
          "sample/" + name + "/Widget",
          null,
          "java/lang/Object",
          null);
      type.visitEnd();
      Path jar = directory.resolve("sample." + name + ".jar");
      try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
        output.putNextEntry(new JarEntry("module-info.class"));
        output.write(descriptor.toByteArray());
        output.closeEntry();
        output.putNextEntry(new JarEntry("sample/" + name + "/Widget.class"));
        output.write(type.toByteArray());
        output.closeEntry();
      }
      artifacts.add(
          new ResolvedJarArtifact(
              new MavenJarIdentity(
                  new dev.w0fv1.norm.value.MavenArtifactCoordinate("sample", name, "1")),
              jar,
              dev.w0fv1.norm.value.Sha256Digest.compute(jar)));
    }
    var root = artifacts.getFirst();
    var graph =
        new ResolvedJarGraph(
            root,
            artifacts,
            artifacts.stream()
                .skip(1)
                .map(artifact -> new JarDependencyEdge(root.identity(), artifact.identity()))
                .toList());
    var classpath = JarBindingClasspath.prepare(List.of(), List.of(graph));
    var moduleRoots = JavaModulePath.inspect(classpath.rootPaths()).names();
    assertEquals(
        List.of("sample.required", "sample.root"),
        JavaModulePath.select(classpath.paths(), moduleRoots).names());
    org.junit.jupiter.api.Assertions.assertThrows(
        java.lang.module.FindException.class,
        () -> JavaModulePath.select(classpath.paths(), List.of("sample.missing")));
    org.junit.jupiter.api.Assertions.assertThrows(
        java.lang.module.FindException.class,
        () -> new JvmJarBindingRuntime(List.of(), List.of(artifacts.getLast().file())));
    try (var runtime = new JvmJarBindingRuntime(List.of(), classpath, List.of())) {
      var loader = runtime.applicationClassLoader();
      assertEquals("sample.root", loader.loadClass("sample.root.Widget").getModule().getName());
      assertEquals(
          "sample.required", loader.loadClass("sample.required.Widget").getModule().getName());
      org.junit.jupiter.api.Assertions.assertFalse(
          loader.loadClass("sample.optional.Widget").getModule().isNamed());
    }
    try (var runtime = JvmJarBindingRuntime.prepared(List.of(), classpath.paths(), moduleRoots)) {
      var loader = runtime.applicationClassLoader();
      assertEquals("sample.root", loader.loadClass("sample.root.Widget").getModule().getName());
      org.junit.jupiter.api.Assertions.assertFalse(
          loader.loadClass("sample.optional.Widget").getModule().isNamed());
    }
  }

  @Test
  void identifiesModulesThatImplementNativeImageFeatures() throws Exception {
    Path jar = directory.resolve("sample.hosted.jar");
    var descriptor = new ClassWriter(0);
    descriptor.visit(Opcodes.V17, Opcodes.ACC_MODULE, "module-info", null, null, null);
    var module = descriptor.visitModule("sample.hosted", 0, null);
    module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
    module.visitPackage("sample/hosted");
    module.visitEnd();
    descriptor.visitEnd();
    var feature = new ClassWriter(0);
    feature.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC,
        "sample/hosted/ImageFeature",
        null,
        "java/lang/Object",
        new String[] {"org/graalvm/nativeimage/hosted/Feature"});
    feature.visitEnd();
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("module-info.class"));
      output.write(descriptor.toByteArray());
      output.closeEntry();
      output.putNextEntry(new JarEntry("sample/hosted/ImageFeature.class"));
      output.write(feature.toByteArray());
      output.closeEntry();
    }
    assertEquals(
        List.of("--add-reads=sample.hosted=org.graalvm.nativeimage"),
        JavaModulePath.inspect(List.of(jar)).nativeImageReadOptions());
  }

  @Test
  void preservesExplicitModuleIdentityInApplicationDependencies() throws Exception {
    Path jar = directory.resolve("sample.widgets.jar");
    ClassWriter descriptor = new ClassWriter(0);
    descriptor.visit(Opcodes.V17, Opcodes.ACC_MODULE, "module-info", null, null, null);
    var module = descriptor.visitModule("sample.widgets", 0, null);
    module.visitRequire("java.base", Opcodes.ACC_MANDATED, null);
    module.visitExport("sample/widgets", 0);
    module.visitEnd();
    descriptor.visitEnd();
    ClassWriter widget = new ClassWriter(0);
    widget.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC, "sample/widgets/Widget", null, "java/lang/Object", null);
    widget.visitEnd();
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("module-info.class"));
      output.write(descriptor.toByteArray());
      output.closeEntry();
      output.putNextEntry(new JarEntry("sample/widgets/Widget.class"));
      output.write(widget.toByteArray());
      output.closeEntry();
    }

    var modules = JavaModulePath.inspect(List.of(jar, directory.resolve("absent-classes")));
    assertEquals(List.of(jar), modules.paths());
    assertEquals(List.of("sample.widgets"), modules.names());
    assertTrue(modules.nativeImageReadOptions().isEmpty());
    try (var runtime = new JvmJarBindingRuntime(List.of(), List.of(jar))) {
      Class<?> type =
          Class.forName("sample.widgets.Widget", false, runtime.applicationClassLoader());
      assertTrue(type.getModule().isNamed());
      assertEquals("sample.widgets", type.getModule().getName());
      assertNotNull(type.getResourceAsStream("Widget.class"));
      assertNotNull(Class.forName(type.getModule(), "sample.widgets.Widget"));
      try (var resource = type.getResourceAsStream("Widget.class")) {
        assertNotNull(resource);
      }
      try (var resource =
          runtime
              .applicationClassLoader()
              .getResource("sample/widgets/Widget.class")
              .openStream()) {
        assertNotNull(resource);
      }
    }
  }
}
