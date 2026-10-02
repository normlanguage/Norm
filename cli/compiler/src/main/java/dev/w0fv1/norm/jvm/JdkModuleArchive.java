package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.value.Sha256Digest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class JdkModuleArchive {
  private static final Map<String, byte[]> SNAPSHOTS = new HashMap<>();

  private JdkModuleArchive() {}

  static synchronized ResolvedJarGraph resolve(Path cache, String name) throws IOException {
    byte[] snapshot = snapshot(name);
    Sha256Digest content = Sha256Digest.compute(snapshot);
    Path directory = Files.createDirectories(cache.resolve(".norm-jdk"));
    Path stable = directory.resolve(name + "-" + content.value() + ".jar");
    Path lockPath = directory.resolve(name + "-" + content.value() + ".lock");
    try (var channel =
            FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        var lock = channel.lock()) {
      if (!Files.isRegularFile(stable) || !content.equals(Sha256Digest.compute(stable))) {
        Path temporary = Files.createTempFile(directory, name + "-", ".jar");
        try {
          Files.write(temporary, snapshot);
          try {
            Files.move(
                temporary,
                stable,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
          } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, stable, StandardCopyOption.REPLACE_EXISTING);
          }
        } finally {
          Files.deleteIfExists(temporary);
        }
      }
      if (!content.equals(Sha256Digest.compute(stable))) {
        throw new IOException("JDK module metadata cache is corrupt: " + stable);
      }
    }
    var artifact = new ResolvedJarArtifact(new JdkModuleIdentity(name), stable, content);
    return new ResolvedJarGraph(artifact, List.of(artifact), List.of());
  }

  static Sha256Digest content(String name) throws IOException {
    return Sha256Digest.compute(snapshot(name));
  }

  private static synchronized byte[] snapshot(String name) throws IOException {
    byte[] cached = SNAPSHOTS.get(name);
    if (cached != null) return cached;
    Path module = FileSystems.getFileSystem(URI.create("jrt:/")).getPath("modules", name);
    if (!Files.isDirectory(module)) throw new IOException("JDK module is unavailable: " + name);
    var runtimeModule =
        java.lang.module.ModuleFinder.ofSystem()
            .find(name)
            .orElseThrow(() -> new IOException("JDK module is unavailable: " + name));
    Set<String> exportedPackages =
        runtimeModule.descriptor().exports().stream()
            .filter(exported -> !exported.isQualified())
            .map(java.lang.module.ModuleDescriptor.Exports::source)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    List<Path> classes;
    try (var paths = Files.walk(module)) {
      classes =
          paths
              .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class"))
              .filter(
                  path -> {
                    Path relative = module.relativize(path);
                    Path parent = relative.getParent();
                    return parent != null
                        && exportedPackages.contains(
                            parent.toString().replace('\\', '.').replace('/', '.'));
                  })
              .sorted(Comparator.comparing(path -> module.relativize(path).toString()))
              .toList();
    }
    var bytes = new ByteArrayOutputStream();
    try (var output = new JarOutputStream(bytes)) {
      for (Path path : classes) {
        byte[] source = Files.readAllBytes(path);
        if ((new ClassReader(source).getAccess() & Opcodes.ACC_PUBLIC) == 0) continue;
        JarEntry entry = new JarEntry(module.relativize(path).toString().replace('\\', '/'));
        entry.setTime(0);
        output.putNextEntry(entry);
        output.write(normalize(source));
        output.closeEntry();
      }
    }
    byte[] snapshot = bytes.toByteArray();
    SNAPSHOTS.put(name, snapshot);
    return snapshot;
  }

  static byte[] normalize(byte[] source) {
    var writer = new ClassWriter(0);
    var publicApi =
        new ClassVisitor(Opcodes.ASM9, writer) {
          @Override
          public FieldVisitor visitField(
              int access, String name, String descriptor, String signature, Object value) {
            if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0
                || (access & Opcodes.ACC_SYNTHETIC) != 0) return null;
            return super.visitField(access, name, descriptor, signature, value);
          }

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) == 0
                || (access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) return null;
            return super.visitMethod(access, name, descriptor, signature, exceptions);
          }

          @Override
          public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0) {
              super.visitInnerClass(name, outerName, innerName, access);
            }
          }

          @Override
          public void visitNestHost(String nestHost) {}

          @Override
          public void visitNestMember(String nestMember) {}
        };
    new ClassReader(source)
        .accept(
            publicApi, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return writer.toByteArray();
  }
}
