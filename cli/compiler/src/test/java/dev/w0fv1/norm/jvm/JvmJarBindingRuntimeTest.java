package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.execution.JarBindingDuration;
import dev.w0fv1.norm.execution.JarBindingInvocationException;
import dev.w0fv1.norm.execution.JarBindingResult;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

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
  void linksGeneratedApplicationClassesWithTheResolvedJarGraph() throws Exception {
    Path jar = temporaryDirectory.resolve("empty.jar");
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
      output.finish();
    }
    Path classes = temporaryDirectory.resolve("application-classes");
    generatedApplicationClass(classes.resolve("sample/Generated.class"));
    ResolvedJarArtifact root =
        new ResolvedJarArtifact(
            new LocalJarIdentity(Sha256Digest.compute(jar)), jar, Sha256Digest.compute(jar));
    ResolvedJarGraph graph = new ResolvedJarGraph(root, List.of(root), List.of());
    JavaBindingCallable callable =
        new JavaBindingCallable(
            "sample.Generated",
            "message",
            "()Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(),
            new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING));
    JavaReferenceType classToken =
        new JavaReferenceType(
            "java.lang.Class",
            JavaReferenceKind.CLASS,
            List.of(JavaBindingTypeArgument.unbounded()));
    JavaBindingCallable echoClass =
        new JavaBindingCallable(
            "sample.Generated",
            "echoClass",
            "(Ljava/lang/Class;)Ljava/lang/Class;",
            JavaCallableKind.STATIC_METHOD,
            List.of(classToken),
            classToken);
    GeneratedJarBinding generated =
        new GeneratedJarBinding(
            List.of(),
            List.of(),
            Map.of("application-message", callable, "application-class", echoClass),
            Map.of(),
            Map.of(),
            Map.of());

    try (JvmJarBindingRuntime runtime =
        new JvmJarBindingRuntime(
            List.of(new ResolvedJarBinding(graph, new JarApiSchema(List.of()), generated)),
            List.of(classes))) {
      assertEquals(
          new JarBindingResult.Scalar("generated"),
          runtime.invoke("application-message", List.of()));
      JarBindingClassReference.Nominal generatedClass =
          new JarBindingClassReference.Nominal(
              new ModuleCoordinate("sample.application", 1), "sample", "Generated");
      assertEquals(
          new JarBindingResult.ClassReference(List.of(generatedClass)),
          runtime.invoke("application-class", List.of(generatedClass)));
    }
  }

  @Test
  void discoversServiceProvidersAcrossBindingModules() throws Exception {
    Path apiJar = serviceApiJar(temporaryDirectory.resolve("service-api.jar"));
    Path implementationJar =
        serviceImplementationJar(temporaryDirectory.resolve("service-implementation.jar"));
    ResolvedJarGraph apiGraph = graph(apiJar);
    ResolvedJarGraph implementationGraph = graph(implementationJar);
    JarApiSchema apiSchema = new JarApiScanner().scan(apiGraph);
    JarApiSchema implementationSchema = new JarApiScanner().scan(implementationGraph);
    GeneratedJarBinding api =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.service.api", 1),
                List.of("ServiceApi"),
                apiGraph.contentId(),
                apiSchema);
    GeneratedJarBinding implementation =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.service.implementation", 1),
                List.of(),
                implementationGraph.contentId(),
                implementationSchema);

    try (JvmJarBindingRuntime runtime =
        new JvmJarBindingRuntime(
            List.of(
                new ResolvedJarBinding(apiGraph, apiSchema, api),
                new ResolvedJarBinding(
                    implementationGraph, implementationSchema, implementation)))) {
      assertEquals(
          new JarBindingResult.Scalar("loaded"),
          runtime.invoke(call(api, "message", "()Ljava/lang/String;"), List.of()));
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
  void keepsApplicationClassesAvailableWhileFrameworkThreadsRetire() throws Exception {
    Path jar = lateFrameworkClassJar(temporaryDirectory.resolve("late-framework-class.jar"));
    ResolvedJarGraph graph = graph(jar);
    GeneratedJarBinding generated =
        new GeneratedJarBinding(List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of());
    JvmJarBindingRuntime runtime =
        new JvmJarBindingRuntime(
            List.of(new ResolvedJarBinding(graph, new JarApiSchema(List.of()), generated)));
    ClassLoader applicationLoader = runtime.applicationClassLoader();
    CountDownLatch closeStarted = new CountDownLatch(1);
    CompletableFuture<Class<?>> loaded = new CompletableFuture<>();
    Thread frameworkThread =
        Thread.ofPlatform()
            .unstarted(
                () -> {
                  try {
                    closeStarted.await();
                    loaded.complete(
                        Class.forName("sample.LateFrameworkClass", true, applicationLoader));
                  } catch (Throwable failure) {
                    loaded.completeExceptionally(failure);
                  }
                });
    frameworkThread.setContextClassLoader(applicationLoader);
    frameworkThread.start();

    CompletableFuture<Void> closing = CompletableFuture.runAsync(runtime::close);
    while (true) {
      try {
        runtime.applicationClassLoader();
        Thread.onSpinWait();
      } catch (JarBindingRuntimeException closed) {
        break;
      }
    }
    closeStarted.countDown();

    assertEquals("sample.LateFrameworkClass", loaded.get(5, TimeUnit.SECONDS).getName());
    closing.get(5, TimeUnit.SECONDS);
    assertThrows(JarBindingRuntimeException.class, runtime::applicationClassLoader);
    frameworkThread.join();
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

  private static ResolvedJarGraph graph(Path jar) throws Exception {
    Sha256Digest content = Sha256Digest.compute(jar);
    ResolvedJarArtifact root = new ResolvedJarArtifact(new LocalJarIdentity(content), jar, content);
    return new ResolvedJarGraph(root, List.of(root), List.of());
  }

  private static Path serviceApiJar(Path path) throws Exception {
    ClassWriter provider = new ClassWriter(0);
    provider.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
        "sample/Provider",
        null,
        "java/lang/Object",
        null);
    provider
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "message",
            "()Ljava/lang/String;",
            null,
            null)
        .visitEnd();
    provider.visitEnd();

    ClassWriter api = new ClassWriter(0);
    api.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
        "sample/ServiceApi",
        null,
        "java/lang/Object",
        null);
    MethodVisitor message =
        api.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "message", "()Ljava/lang/String;", null, null);
    message.visitCode();
    message.visitLdcInsn(Type.getType("Lsample/Provider;"));
    message.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/util/ServiceLoader",
        "load",
        "(Ljava/lang/Class;)Ljava/util/ServiceLoader;",
        false);
    message.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/util/ServiceLoader",
        "findFirst",
        "()Ljava/util/Optional;",
        false);
    message.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/util/Optional", "orElseThrow", "()Ljava/lang/Object;", false);
    message.visitTypeInsn(Opcodes.CHECKCAST, "sample/Provider");
    message.visitMethodInsn(
        Opcodes.INVOKEINTERFACE, "sample/Provider", "message", "()Ljava/lang/String;", true);
    message.visitInsn(Opcodes.ARETURN);
    message.visitMaxs(1, 0);
    message.visitEnd();
    api.visitEnd();

    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      writeClass(output, "sample/Provider.class", provider);
      writeClass(output, "sample/ServiceApi.class", api);
    }
    return path;
  }

  private static Path serviceImplementationJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
        "sample/ProviderImpl",
        null,
        "java/lang/Object",
        new String[] {"sample/Provider"});
    MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(1, 1);
    constructor.visitEnd();
    MethodVisitor message =
        writer.visitMethod(Opcodes.ACC_PUBLIC, "message", "()Ljava/lang/String;", null, null);
    message.visitCode();
    message.visitLdcInsn("loaded");
    message.visitInsn(Opcodes.ARETURN);
    message.visitMaxs(1, 1);
    message.visitEnd();
    writer.visitEnd();

    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      writeClass(output, "sample/ProviderImpl.class", writer);
      output.putNextEntry(new JarEntry("META-INF/services/sample.Provider"));
      output.write("sample.ProviderImpl\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      output.closeEntry();
    }
    return path;
  }

  private static void writeClass(JarOutputStream output, String name, ClassWriter writer)
      throws Exception {
    output.putNextEntry(new JarEntry(name));
    output.write(writer.toByteArray());
    output.closeEntry();
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

  private static void generatedApplicationClass(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/Generated",
        null,
        "java/lang/Object",
        null);
    MethodVisitor message =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "message", "()Ljava/lang/String;", null, null);
    message.visitCode();
    message.visitLdcInsn("generated");
    message.visitInsn(Opcodes.ARETURN);
    message.visitMaxs(1, 0);
    message.visitEnd();
    MethodVisitor echoClass =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "echoClass",
            "(Ljava/lang/Class;)Ljava/lang/Class;",
            null,
            null);
    echoClass.visitCode();
    echoClass.visitVarInsn(Opcodes.ALOAD, 0);
    echoClass.visitInsn(Opcodes.ARETURN);
    echoClass.visitMaxs(1, 1);
    echoClass.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    Files.write(path, writer.toByteArray());
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

  private static Path lateFrameworkClassJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/LateFrameworkClass",
        null,
        "java/lang/Object",
        null);
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/LateFrameworkClass.class"));
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
