package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import dev.w0fv1.norm.value.Sha256Digest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

final class JdkModuleArchiveTest {
  @TempDir java.nio.file.Path temporaryDirectory;

  @Test
  void metadataLeaseProtectsActiveContentFromEviction() throws Exception {
    var metadata = JdkModuleArchive.open(temporaryDirectory, "java.base");
    var file = metadata.graph().root().file();
    var cache =
        new dev.w0fv1.norm.core.store.DirectoryArtifactCache(
            temporaryDirectory.resolve("java-metadata"), 1, 1);
    var key = Sha256Digest.compute(new byte[] {1});
    try (var other =
        cache.acquire(
            key,
            root -> true,
            root -> java.nio.file.Files.write(root.resolve("other.bin"), new byte[] {1}))) {
      org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.isRegularFile(file));
      metadata.close();
      org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, metadata::graph);
      try (var next =
          cache.acquire(
              Sha256Digest.compute(new byte[] {2}),
              root -> true,
              root -> java.nio.file.Files.write(root.resolve("next.bin"), new byte[] {2}))) {
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(file));
      }
    }
  }

  @Test
  void concurrentResolutionPublishesAndRepairsVerifiedMetadata() throws Exception {
    var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      var requests = new java.util.ArrayList<java.util.concurrent.Future<JdkModuleArchive>>();
      for (int index = 0; index < 8; index++) {
        requests.add(
            pool.submit(
                () -> {
                  start.await();
                  return JdkModuleArchive.open(temporaryDirectory, "java.base");
                }));
      }
      start.countDown();
      var first = requests.getFirst().get().graph();
      for (var request : requests)
        assertEquals(first.contentId(), request.get().graph().contentId());
      assertEquals(first.root().content(), Sha256Digest.compute(first.root().file()));
      java.nio.file.Files.writeString(first.root().file(), "corrupt");
      try (var archive = JdkModuleArchive.open(temporaryDirectory, "java.base")) {
        var repaired = archive.graph();
        assertEquals(first.contentId(), repaired.contentId());
        assertEquals(repaired.root().content(), Sha256Digest.compute(repaired.root().file()));
      }
      for (var request : requests) request.get().close();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void moduleIdentitySeparatesEqualMetadata() throws Exception {
    var content = Sha256Digest.compute(new byte[0]);
    var path = java.nio.file.Path.of("empty.jar");
    var base = new ResolvedJarArtifact(new JdkModuleIdentity("java.base"), path, content);
    var logging = new ResolvedJarArtifact(new JdkModuleIdentity("java.logging"), path, content);
    assertNotEquals(
        new ResolvedJarGraph(base, java.util.List.of(base), java.util.List.of()).contentId(),
        new ResolvedJarGraph(logging, java.util.List.of(logging), java.util.List.of()).contentId());
  }

  @Test
  void publicApiFingerprintIgnoresBodiesAndPrivateMembers() {
    var original = Sha256Digest.compute(JdkModuleArchive.normalize(type("()I", 1, 1)));
    var implementationChange = Sha256Digest.compute(JdkModuleArchive.normalize(type("()I", 2, 2)));
    var apiChange = Sha256Digest.compute(JdkModuleArchive.normalize(type("()J", 1, 1)));
    assertEquals(original, implementationChange);
    assertNotEquals(original, apiChange);
  }

  private static byte[] type(String result, int returned, int hidden) {
    var writer = new ClassWriter(0);
    writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "test/Example", null, "java/lang/Object", null);
    var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "value", result, null, null);
    method.visitCode();
    method.visitLdcInsn(returned);
    method.visitInsn(result.equals("()J") ? Opcodes.LRETURN : Opcodes.IRETURN);
    method.visitMaxs(2, 1);
    method.visitEnd();
    var privateMethod = writer.visitMethod(Opcodes.ACC_PRIVATE, "hidden", "()I", null, null);
    privateMethod.visitCode();
    privateMethod.visitLdcInsn(hidden);
    privateMethod.visitInsn(Opcodes.IRETURN);
    privateMethod.visitMaxs(1, 1);
    privateMethod.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }
}
