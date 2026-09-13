package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class JarApiCacheTest {
  @TempDir Path directory;

  @Test
  void reusesSelectedApiAndRejectsChangedJarContent() throws Exception {
    Path jar = directory.resolve("sample.jar");
    var type = new ClassWriter(0);
    type.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
        "sample/Value",
        null,
        "java/lang/Object",
        null);
    type.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "value", "()I", null, null)
        .visitEnd();
    type.visitEnd();
    try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.putNextEntry(new JarEntry("sample/Value.class"));
      output.write(type.toByteArray());
      output.closeEntry();
    }
    var digest = Sha256Digest.compute(jar);
    var artifact = new ResolvedJarArtifact(new LocalJarIdentity(digest), jar, digest);
    var graph = new ResolvedJarGraph(artifact, List.of(artifact), List.of());
    Path storage = directory.resolve("cache");
    var first = new JarApiCache(storage).scan(graph, List.of("sample.Value"), true);
    var second = new JarApiCache(storage).scan(graph, List.of("sample.Value"), true);
    assertEquals(first, second);
    assertFalse(second.allTypes().isEmpty());
    assertTrue(new JarApiCache(storage).scan(graph, List.of(), true).allTypes().isEmpty());
    try (var files = Files.list(storage)) {
      assertEquals(1, files.filter(file -> file.toString().endsWith(".bin")).count());
    }
    Files.writeString(jar, "changed");
    assertThrows(
        java.io.IOException.class,
        () -> new JarApiCache(storage).scan(graph, List.of("sample.Value"), true));
  }
}
