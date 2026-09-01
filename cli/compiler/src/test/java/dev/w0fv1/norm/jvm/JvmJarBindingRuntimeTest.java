package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.JarBindingDuration;
import dev.w0fv1.norm.execution.JarBindingInvocationException;
import dev.w0fv1.norm.execution.JarBindingResult;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class JvmJarBindingRuntimeTest {
  @TempDir Path temporaryDirectory;

  @Test
  void invokesAResolvedApacheCommonsLangMethod() throws Exception {
    var target =
        new MavenJarTarget(
            new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
            Optional.empty());
    try (JarResolver resolver = new JarResolver(temporaryDirectory.resolve("maven-cache"))) {
      ResolvedJarGraph graph = resolver.resolve(temporaryDirectory, new JarBinding(target));
      JarApiSchema schema = new JarApiScanner().scan(graph);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generate(
                  new ModuleCoordinate("commons.lang", 1),
                  List.of("StringUtils"),
                  graph.contentId(),
                  schema);
      String reverseCall =
          generated.calls().entrySet().stream()
              .filter(
                  entry ->
                      entry.getValue().name().equals("reverse")
                          && entry
                              .getValue()
                              .descriptor()
                              .equals("(Ljava/lang/String;)Ljava/lang/String;"))
              .map(java.util.Map.Entry::getKey)
              .findFirst()
              .orElseThrow();

      try (JvmJarBindingRuntime runtime =
          new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
        assertEquals(
            new JarBindingResult.Scalar("mroN"), runtime.invoke(reverseCall, List.of("Norm")));
        assertEquals(
            JarBindingResult.Null.INSTANCE,
            runtime.invoke(reverseCall, java.util.Collections.singletonList(null)));
      }
    }
  }

  @Test
  void invokesJavaCharSequenceMethodsWithNormStrings() throws Exception {
    var target =
        new MavenJarTarget(
            new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
            Optional.empty());
    try (JarResolver resolver = new JarResolver(temporaryDirectory.resolve("text-cache"))) {
      ResolvedJarGraph graph = resolver.resolve(temporaryDirectory, new JarBinding(target));
      JarApiSchema schema = new JarApiScanner().scan(graph);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generate(
                  new ModuleCoordinate("commons.lang", 1),
                  List.of("StringUtils"),
                  graph.contentId(),
                  schema);
      String isBlank = call(generated, "isBlank", "(Ljava/lang/CharSequence;)Z");

      try (JvmJarBindingRuntime runtime =
          new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
        assertEquals(new JarBindingResult.Scalar(true), runtime.invoke(isBlank, List.of("  ")));
        assertEquals(new JarBindingResult.Scalar(false), runtime.invoke(isBlank, List.of("Norm")));
      }
    }
  }

  @Test
  void readsAResolvedApacheCommonsLangStaticField() throws Exception {
    var target =
        new MavenJarTarget(
            new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
            Optional.empty());
    try (JarResolver resolver = new JarResolver(temporaryDirectory.resolve("field-cache"))) {
      ResolvedJarGraph graph = resolver.resolve(temporaryDirectory, new JarBinding(target));
      JarApiSchema schema = new JarApiScanner().scan(graph);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generate(
                  new ModuleCoordinate("commons.lang", 1),
                  List.of("StringUtils"),
                  graph.contentId(),
                  schema);
      String empty =
          call(generated, JavaCallableKind.STATIC_FIELD_GET, "EMPTY", "Ljava/lang/String;");

      try (JvmJarBindingRuntime runtime =
          new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
        assertEquals(new JarBindingResult.Scalar(""), runtime.invoke(empty, List.of()));
      }
    }
  }

  @Test
  void invokesAReifiedGenericApacheCommonsLangMethod() throws Exception {
    var target =
        new MavenJarTarget(
            new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
            Optional.empty());
    try (JarResolver resolver = resolver("generic-cache")) {
      ResolvedJarGraph graph = resolver.resolve(temporaryDirectory, new JarBinding(target));
      JarApiSchema schema = new JarApiScanner().scan(graph);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generate(
                  new ModuleCoordinate("commons.lang", 1),
                  List.of("ObjectUtils"),
                  graph.contentId(),
                  schema);
      String identity = call(generated, "CONST", "(Ljava/lang/Object;)Ljava/lang/Object;");

      try (JvmJarBindingRuntime runtime =
          new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
        assertEquals(
            new JarBindingResult.Scalar("Norm"), runtime.invoke(identity, List.of("Norm")));
        assertEquals(new JarBindingResult.Scalar(7), runtime.invoke(identity, List.of(7)));
      }
    }
  }

  @Test
  void invokesCharacterAndBoxedPrimitiveMethods() throws Exception {
    var target =
        new MavenJarTarget(
            new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
            Optional.empty());
    try (JarResolver resolver = resolver("primitive-cache")) {
      ResolvedJarGraph graph = resolver.resolve(temporaryDirectory, new JarBinding(target));
      JarApiSchema schema = new JarApiScanner().scan(graph);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generate(
                  new ModuleCoordinate("commons.lang", 1),
                  List.of("BooleanUtils", "ObjectUtils"),
                  graph.contentId(),
                  schema);
      String character = call(generated, "CONST", "(C)C");
      String negate = call(generated, "negate", "(Ljava/lang/Boolean;)Ljava/lang/Boolean;");

      try (JvmJarBindingRuntime runtime =
          new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
        assertEquals(new JarBindingResult.Scalar(65), runtime.invoke(character, List.of(65)));
        assertEquals(new JarBindingResult.Scalar(false), runtime.invoke(negate, List.of(true)));
        assertEquals(
            JarBindingResult.Null.INSTANCE,
            runtime.invoke(negate, java.util.Collections.singletonList(null)));
      }
    }
  }

  @Test
  void preservesMutableJavaArrayIdentity() throws Exception {
    var target =
        new MavenJarTarget(
            new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
            Optional.empty());
    try (JarResolver resolver = resolver("array-cache")) {
      ResolvedJarGraph graph = resolver.resolve(temporaryDirectory, new JarBinding(target));
      JarApiSchema schema = new JarApiScanner().scan(graph);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generate(
                  new ModuleCoordinate("commons.lang", 1),
                  List.of("StringUtils"),
                  graph.contentId(),
                  schema);
      String split =
          call(generated, "split", "(Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;");
      String length =
          call(generated, JavaCallableKind.ARRAY_LENGTH, "[Ljava/lang/String;", "length", "()I");
      String get =
          call(
              generated,
              JavaCallableKind.ARRAY_GET,
              "[Ljava/lang/String;",
              "get",
              "(I)Ljava/lang/String;");
      String set =
          call(
              generated,
              JavaCallableKind.ARRAY_SET,
              "[Ljava/lang/String;",
              "set",
              "(ILjava/lang/String;)V");

      try (JvmJarBindingRuntime runtime =
          new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
        JarBindingResult.Reference values =
            (JarBindingResult.Reference) runtime.invoke(split, List.of("Norm,Java", ","));
        assertEquals(
            new JarBindingResult.Scalar(2), runtime.invoke(length, List.of(values.value())));
        assertEquals(
            new JarBindingResult.Scalar("Norm"), runtime.invoke(get, List.of(values.value(), 0)));
        assertEquals(
            JarBindingResult.Void.INSTANCE, runtime.invoke(set, List.of(values.value(), 0, "NAR")));
        assertEquals(
            new JarBindingResult.Scalar("NAR"), runtime.invoke(get, List.of(values.value(), 0)));
      }
    }
  }

  @Test
  void invokesGenericJavaVarargsThroughAnObjectArray() throws Exception {
    var target =
        new MavenJarTarget(
            new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
            Optional.empty());
    try (JarResolver resolver = resolver("generic-array-cache")) {
      ResolvedJarGraph graph = resolver.resolve(temporaryDirectory, new JarBinding(target));
      JarApiSchema schema = new JarApiScanner().scan(graph);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generate(
                  new ModuleCoordinate("commons.lang", 1),
                  List.of("ObjectUtils"),
                  graph.contentId(),
                  schema);
      String constructor =
          call(
              generated,
              JavaCallableKind.ARRAY_CONSTRUCTOR,
              "[Ljava/lang/Object;",
              "<array>",
              "(I)[Ljava/lang/Object;");
      String set =
          call(
              generated,
              JavaCallableKind.ARRAY_SET,
              "[Ljava/lang/Object;",
              "set",
              "(ILjava/lang/Object;)V");
      String first = call(generated, "firstNonNull", "([Ljava/lang/Object;)Ljava/lang/Object;");

      try (JvmJarBindingRuntime runtime =
          new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
        JarBindingResult.Reference values =
            (JarBindingResult.Reference) runtime.invoke(constructor, List.of(2));
        assertEquals(
            JarBindingResult.Void.INSTANCE,
            runtime.invoke(set, List.of(values.value(), 0, "first")));
        assertEquals(
            new JarBindingResult.Scalar("first"), runtime.invoke(first, List.of(values.value())));
      }
    }
  }

  @Test
  void constructsAndInvokesAnApacheCommonsLangObject() throws Exception {
    var target =
        new MavenJarTarget(
            new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0"),
            Optional.empty());
    try (JarResolver resolver = new JarResolver(temporaryDirectory.resolve("object-cache"))) {
      ResolvedJarGraph graph = resolver.resolve(temporaryDirectory, new JarBinding(target));
      JarApiSchema schema = new JarApiScanner().scan(graph);
      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generate(
                  new ModuleCoordinate("commons.lang", 1),
                  List.of("mutable.MutableInt"),
                  graph.contentId(),
                  schema);
      String constructor = call(generated, "<init>", "(I)V");
      String increment = call(generated, "increment", "()V");
      String intValue = call(generated, "intValue", "()I");

      try (JvmJarBindingRuntime runtime =
          new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
        JarBindingResult.Reference instance =
            (JarBindingResult.Reference) runtime.invoke(constructor, List.of(41));
        assertEquals(
            new JarBindingResult.Scalar(41), runtime.invoke(intValue, List.of(instance.value())));
        assertEquals(
            JarBindingResult.Void.INSTANCE, runtime.invoke(increment, List.of(instance.value())));
        assertEquals(
            new JarBindingResult.Scalar(42), runtime.invoke(intValue, List.of(instance.value())));
      }
    }
  }

  @Test
  void readsAndWritesAnInstanceField() throws Exception {
    Path jar = mutableValueJar(temporaryDirectory.resolve("mutable-value.jar"));
    ResolvedJarArtifact root =
        new ResolvedJarArtifact(
            new LocalJarIdentity(Sha256Digest.compute(jar)), jar, Sha256Digest.compute(jar));
    ResolvedJarGraph graph = new ResolvedJarGraph(root, List.of(root), List.of());
    JarApiSchema schema = new JarApiScanner().scan(graph);
    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("MutableValue"),
                graph.contentId(),
                schema);
    String constructor = call(generated, "<init>", "(I)V");
    String getter = call(generated, JavaCallableKind.INSTANCE_FIELD_GET, "value", "I");
    String setter = call(generated, JavaCallableKind.INSTANCE_FIELD_SET, "value", "I");

    try (JvmJarBindingRuntime runtime =
        new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
      JarBindingResult.Reference instance =
          (JarBindingResult.Reference) runtime.invoke(constructor, List.of(7));
      assertEquals(
          new JarBindingResult.Scalar(7), runtime.invoke(getter, List.of(instance.value())));
      assertEquals(
          JarBindingResult.Void.INSTANCE, runtime.invoke(setter, List.of(instance.value(), 9)));
      assertEquals(
          new JarBindingResult.Scalar(9), runtime.invoke(getter, List.of(instance.value())));
    }
  }

  @Test
  void preservesJavaInvocationFailures() throws Exception {
    Path jar = failureJar(temporaryDirectory.resolve("failure.jar"));
    ResolvedJarArtifact root =
        new ResolvedJarArtifact(
            new LocalJarIdentity(Sha256Digest.compute(jar)), jar, Sha256Digest.compute(jar));
    ResolvedJarGraph graph = new ResolvedJarGraph(root, List.of(root), List.of());
    JarApiSchema schema = new JarApiScanner().scan(graph);
    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("FailureApi"),
                graph.contentId(),
                schema);
    String failure = call(generated, "fail", "(Ljava/lang/String;)Ljava/lang/String;");
    String identity = call(generated, "identity", "(Ljava/lang/Throwable;)Ljava/lang/Throwable;");

    try (JvmJarBindingRuntime runtime =
        new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
      JarBindingInvocationException exception =
          assertThrows(
              JarBindingInvocationException.class, () -> runtime.invoke(failure, List.of("boom")));
      IllegalStateException cause =
          assertInstanceOf(IllegalStateException.class, exception.failure());
      assertEquals("boom", cause.getMessage());
      assertEquals(
          new JarBindingResult.ExceptionReference(cause), runtime.invoke(identity, List.of(cause)));
    }
  }

  @Test
  void convertsCharsetNamesAtTheJavaBoundary() throws Exception {
    Path jar = charsetJar(temporaryDirectory.resolve("charset.jar"));
    ResolvedJarArtifact root =
        new ResolvedJarArtifact(
            new LocalJarIdentity(Sha256Digest.compute(jar)), jar, Sha256Digest.compute(jar));
    ResolvedJarGraph graph = new ResolvedJarGraph(root, List.of(root), List.of());
    JarApiSchema schema = new JarApiScanner().scan(graph);
    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("CharsetApi"),
                graph.contentId(),
                schema);
    String canonical =
        call(generated, "canonical", "(Ljava/nio/charset/Charset;)Ljava/nio/charset/Charset;");
    String object = call(generated, "object", "(Ljava/lang/Object;)Ljava/lang/Object;");

    try (JvmJarBindingRuntime runtime =
        new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
      assertEquals(
          new JarBindingResult.Scalar("UTF-8"), runtime.invoke(canonical, List.of("utf-8")));
      assertEquals(
          JarBindingResult.Null.INSTANCE,
          runtime.invoke(canonical, java.util.Collections.singletonList(null)));
      assertEquals(new JarBindingResult.Scalar("Norm"), runtime.invoke(object, List.of("Norm")));
      assertEquals(new JarBindingResult.Scalar(42), runtime.invoke(object, List.of(42)));
      assertEquals(
          JarBindingResult.Null.INSTANCE,
          runtime.invoke(object, java.util.Collections.singletonList(null)));
    }
  }

  @Test
  void convertsDurationsAtTheJavaBoundary() throws Exception {
    Path jar = durationJar(temporaryDirectory.resolve("duration.jar"));
    ResolvedJarArtifact root =
        new ResolvedJarArtifact(
            new LocalJarIdentity(Sha256Digest.compute(jar)), jar, Sha256Digest.compute(jar));
    ResolvedJarGraph graph = new ResolvedJarGraph(root, List.of(root), List.of());
    JarApiSchema schema = new JarApiScanner().scan(graph);
    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("DurationApi"),
                graph.contentId(),
                schema);
    String identity = call(generated, "identity", "(Ljava/time/Duration;)Ljava/time/Duration;");

    try (JvmJarBindingRuntime runtime =
        new JvmJarBindingRuntime(List.of(new ResolvedJarBinding(graph, schema, generated)))) {
      assertEquals(
          new JarBindingResult.DurationValue(5, 7),
          runtime.invoke(identity, List.of(new JarBindingDuration(5, 7))));
      assertEquals(
          JarBindingResult.Null.INSTANCE,
          runtime.invoke(identity, java.util.Collections.singletonList(null)));
    }
  }

  private static String call(GeneratedJarBinding generated, String name, String descriptor) {
    return generated.calls().entrySet().stream()
        .filter(
            entry ->
                entry.getValue().name().equals(name)
                    && entry.getValue().descriptor().equals(descriptor))
        .map(java.util.Map.Entry::getKey)
        .findFirst()
        .orElseThrow();
  }

  private JarResolver resolver(String directory) {
    return new JarResolver(temporaryDirectory.resolve(directory));
  }

  private static String call(
      GeneratedJarBinding generated, JavaCallableKind kind, String name, String descriptor) {
    return generated.calls().entrySet().stream()
        .filter(
            entry ->
                entry.getValue().kind() == kind
                    && entry.getValue().name().equals(name)
                    && entry.getValue().descriptor().equals(descriptor))
        .map(java.util.Map.Entry::getKey)
        .findFirst()
        .orElseThrow();
  }

  private static String call(
      GeneratedJarBinding generated,
      JavaCallableKind kind,
      String owner,
      String name,
      String descriptor) {
    return generated.calls().entrySet().stream()
        .filter(
            entry ->
                entry.getValue().kind() == kind
                    && entry.getValue().owner().equals(owner)
                    && entry.getValue().name().equals(name)
                    && entry.getValue().descriptor().equals(descriptor))
        .map(java.util.Map.Entry::getKey)
        .findFirst()
        .orElseThrow();
  }

  private static Path mutableValueJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/MutableValue",
        null,
        "java/lang/Object",
        null);
    writer.visitField(Opcodes.ACC_PUBLIC, "value", "I", null, null).visitEnd();
    MethodVisitor constructor =
        writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(I)V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitVarInsn(Opcodes.ILOAD, 1);
    constructor.visitFieldInsn(Opcodes.PUTFIELD, "sample/MutableValue", "value", "I");
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(2, 2);
    constructor.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/MutableValue.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path failureJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/FailureApi",
        null,
        "java/lang/Object",
        null);
    MethodVisitor failure =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "fail",
            "(Ljava/lang/String;)Ljava/lang/String;",
            null,
            null);
    failure.visitCode();
    failure.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
    failure.visitInsn(Opcodes.DUP);
    failure.visitVarInsn(Opcodes.ALOAD, 0);
    failure.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "java/lang/IllegalStateException",
        "<init>",
        "(Ljava/lang/String;)V",
        false);
    failure.visitInsn(Opcodes.ATHROW);
    failure.visitMaxs(3, 1);
    failure.visitEnd();
    MethodVisitor identity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "identity",
            "(Ljava/lang/Throwable;)Ljava/lang/Throwable;",
            null,
            null);
    identity.visitCode();
    identity.visitVarInsn(Opcodes.ALOAD, 0);
    identity.visitInsn(Opcodes.ARETURN);
    identity.visitMaxs(1, 1);
    identity.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/FailureApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path charsetJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/CharsetApi",
        null,
        "java/lang/Object",
        null);
    MethodVisitor canonical =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "canonical",
            "(Ljava/nio/charset/Charset;)Ljava/nio/charset/Charset;",
            null,
            null);
    canonical.visitCode();
    canonical.visitVarInsn(Opcodes.ALOAD, 0);
    canonical.visitInsn(Opcodes.ARETURN);
    canonical.visitMaxs(1, 1);
    canonical.visitEnd();
    MethodVisitor object =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "object",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            null,
            null);
    object.visitCode();
    object.visitVarInsn(Opcodes.ALOAD, 0);
    object.visitInsn(Opcodes.ARETURN);
    object.visitMaxs(1, 1);
    object.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/CharsetApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path durationJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/DurationApi",
        null,
        "java/lang/Object",
        null);
    MethodVisitor identity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "identity",
            "(Ljava/time/Duration;)Ljava/time/Duration;",
            null,
            null);
    identity.visitCode();
    identity.visitVarInsn(Opcodes.ALOAD, 0);
    identity.visitInsn(Opcodes.ARETURN);
    identity.visitMaxs(1, 1);
    identity.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/DurationApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }
}
