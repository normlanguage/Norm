package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class JavaModuleLoadingTest {
  @TempDir Path directory;

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
