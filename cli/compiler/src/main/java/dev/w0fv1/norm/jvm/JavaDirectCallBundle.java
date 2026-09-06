package dev.w0fv1.norm.jvm;

import static org.objectweb.asm.Opcodes.*;

import dev.w0fv1.norm.bridge.JavaDirectCallRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;

public final class JavaDirectCallBundle {
  public static final String REGISTRY_NAME = "dev.w0fv1.norm.generated.binding.Calls";

  public void write(List<LinkedJarBinding> bindings, Path destination, ClassLoader loader)
      throws IOException {
    var calls = new TreeMap<String, JavaBindingCallable>();
    for (var binding : bindings) {
      for (var entry : binding.calls().entrySet()) {
        if (calls.putIfAbsent(entry.getKey(), entry.getValue()) != null)
          throw new IllegalArgumentException("duplicate direct Java call " + entry.getKey());
      }
    }
    write(REGISTRY_NAME, calls, destination, loader);
  }

  public void write(
      String binaryName,
      java.util.Map<String, ? extends JavaCallTarget> calls,
      Path destination,
      ClassLoader loader)
      throws IOException {
    var generator = new JavaDirectCallGenerator();
    var registry = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    String registryName = binaryName.replace('.', '/');
    registry.visit(
        V17,
        ACC_PUBLIC | ACC_FINAL,
        registryName,
        null,
        "java/lang/Object",
        new String[] {Type.getInternalName(JavaDirectCallRegistry.class)});
    var constructor = registry.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(ALOAD, 0);
    constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitInsn(RETURN);
    constructor.visitMaxs(0, 0);
    constructor.visitEnd();
    var factory = registry.visitMethod(ACC_PUBLIC, "calls", "()Ljava/util/Map;", null, null);
    factory.visitCode();
    factory.visitTypeInsn(NEW, "java/util/LinkedHashMap");
    factory.visitInsn(DUP);
    factory.visitMethodInsn(INVOKESPECIAL, "java/util/LinkedHashMap", "<init>", "()V", false);
    factory.visitVarInsn(ASTORE, 1);
    var entries = List.copyOf(new TreeMap<>(calls).entrySet());
    for (int offset = 0; offset < entries.size(); offset += 128) {
      String groupName = registryName + "$Group" + offset / 128;
      var group = new ClassWriter(ClassWriter.COMPUTE_MAXS);
      group.visit(V17, ACC_PUBLIC | ACC_FINAL, groupName, null, "java/lang/Object", null);
      var fill =
          group.visitMethod(ACC_PUBLIC | ACC_STATIC, "fill", "(Ljava/util/Map;)V", null, null);
      fill.visitCode();
      for (int index = offset; index < Math.min(offset + 128, entries.size()); index++) {
        var entry = entries.get(index);
        String name = registryName + "$Call" + index;
        try {
          Path file = destination.resolve(name + ".class");
          Files.createDirectories(file.getParent());
          Files.write(file, generator.generate(name.replace('/', '.'), entry.getValue(), loader));
        } catch (ClassNotFoundException exception) {
          throw new IOException("cannot generate direct Java call " + entry.getKey(), exception);
        }
        fill.visitVarInsn(ALOAD, 0);
        fill.visitLdcInsn(entry.getKey());
        fill.visitTypeInsn(NEW, name);
        fill.visitInsn(DUP);
        fill.visitMethodInsn(INVOKESPECIAL, name, "<init>", "()V", false);
        fill.visitMethodInsn(
            INVOKEINTERFACE,
            "java/util/Map",
            "put",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            true);
        fill.visitInsn(POP);
      }
      fill.visitInsn(RETURN);
      fill.visitMaxs(0, 0);
      fill.visitEnd();
      group.visitEnd();
      Path groupFile = destination.resolve(groupName + ".class");
      Files.createDirectories(groupFile.getParent());
      Files.write(groupFile, group.toByteArray());
      factory.visitVarInsn(ALOAD, 1);
      factory.visitMethodInsn(INVOKESTATIC, groupName, "fill", "(Ljava/util/Map;)V", false);
    }
    factory.visitVarInsn(ALOAD, 1);
    factory.visitInsn(ARETURN);
    factory.visitMaxs(0, 0);
    factory.visitEnd();
    registry.visitEnd();
    Path file = destination.resolve(registryName + ".class");
    Files.createDirectories(file.getParent());
    Files.write(file, registry.toByteArray());
  }
}
