package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.bridge.NormApplicationMethod;
import dev.w0fv1.norm.core.DefinitionId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class JavaApplicationMethodIndex {
  public static final String REGISTRY_NAME = "dev.w0fv1.norm.generated.application.Calls";

  private JavaApplicationMethodIndex() {}

  public static Analysis analyze(Path classes, List<JavaAnnotationStub> stubs) throws IOException {
    var calls = new TreeMap<DefinitionId, IndexedMethod>();
    for (var stub : stubs) {
      var reader =
          new ClassReader(
              Files.readAllBytes(classes.resolve(stub.binaryName().replace('.', '/') + ".class")));
      if (!reader.getClassName().replace('/', '.').equals(stub.binaryName()))
        throw new IOException("Application class identity mismatch: " + stub.binaryName());
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
              if ((access & Opcodes.ACC_PUBLIC) == 0
                  || (access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) return null;
              return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                  if (!annotation.equals(Type.getDescriptor(NormApplicationMethod.class)))
                    return null;
                  return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String member, Object value) {
                      if (!member.equals("value")) return;
                      var id = DefinitionId.parse((String) value);
                      var target = new IndexedMethod(stub.binaryName(), name, descriptor, access);
                      var existing = calls.putIfAbsent(id, target);
                      if (existing != null && !existing.equals(target))
                        throw new IllegalArgumentException(
                            "Conflicting application method "
                                + id
                                + ": "
                                + existing
                                + " and "
                                + target);
                    }
                  };
                }
              };
            }
          },
          ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }
    var instances = new TreeMap<DefinitionId, Target>();
    var entries = new java.util.TreeSet<DefinitionId>();
    calls.forEach(
        (id, method) -> {
          if ((method.access() & Opcodes.ACC_STATIC) == 0 && !method.name().equals("<init>"))
            instances.put(id, new Target(method.owner(), method.name(), method.descriptor()));
          if ((method.access() & Opcodes.ACC_ABSTRACT) == 0) entries.add(id);
        });
    return new Analysis(instances, entries);
  }

  public record Analysis(Map<DefinitionId, Target> instanceMethods, Set<DefinitionId> entryPoints) {
    public Analysis {
      instanceMethods = Collections.unmodifiableMap(new TreeMap<>(instanceMethods));
      entryPoints = Collections.unmodifiableSet(new java.util.TreeSet<>(entryPoints));
    }
  }

  private record IndexedMethod(String owner, String name, String descriptor, int access) {}

  public record Target(String owner, String name, String descriptor) implements JavaCallTarget {
    @Override
    public JavaCallableKind kind() {
      return JavaCallableKind.INSTANCE_METHOD;
    }
  }
}
