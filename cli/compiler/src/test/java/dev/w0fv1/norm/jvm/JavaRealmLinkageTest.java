package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaRealmLinkageTest {
  @TempDir Path directory;

  @Test
  void independentApplicationRealmsKeepFacadeCallbacksIsolated() throws Exception {
    var output =
        compile(
            "isolated",
            Map.of(
                "fixture/Facade.java",
                """
        package fixture;
        public final class Facade {
          public static Object call() {
            return dev.w0fv1.norm.bridge.JavaApplicationBridge.toJava(Facade.class.getClassLoader(), "value");
          }
        }
        """),
            Set.of());
    var firstHandler =
        (dev.w0fv1.norm.bridge.JavaApplicationBridge.Handler)
            java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {dev.w0fv1.norm.bridge.JavaApplicationBridge.Handler.class},
                (proxy, method, arguments) -> "first");
    var secondHandler =
        (dev.w0fv1.norm.bridge.JavaApplicationBridge.Handler)
            java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {dev.w0fv1.norm.bridge.JavaApplicationBridge.Handler.class},
                (proxy, method, arguments) -> "second");
    try (var first =
            JvmJarBindingRuntime.prepared(List.of(), List.of(output.classes()), List.of());
        var second =
            JvmJarBindingRuntime.prepared(List.of(), List.of(output.classes()), List.of())) {
      Class<?> firstFacade = first.applicationClassLoader().loadClass("fixture.Facade");
      Class<?> secondFacade = second.applicationClassLoader().loadClass("fixture.Facade");
      try (var firstBridge =
              dev.w0fv1.norm.bridge.JavaApplicationBridge.replace(
                  firstFacade.getClassLoader(), firstHandler);
          var secondBridge =
              dev.w0fv1.norm.bridge.JavaApplicationBridge.replace(
                  secondFacade.getClassLoader(), secondHandler)) {
        assertEquals("first", firstFacade.getMethod("call").invoke(null));
        assertEquals("second", secondFacade.getMethod("call").invoke(null));
        assertNotSame(firstFacade, secondFacade);
      }
    }
  }

  @Test
  void escapedJavaValuesRetainTheirStaticDependencyResources() throws Exception {
    var output =
        compile(
            "escaped",
            Map.of(
                "fixture/Foo.java",
                    "package fixture; public final class Foo { public static Foo create() { return new Foo(); } public int read() { return Bar.value(); } }",
                "fixture/Bar.java",
                    "package fixture; public final class Bar { public static int value() { return 42; } }"),
            Set.of("fixture/Foo.class"));
    var reference = new JavaReferenceType("fixture.Foo", JavaReferenceKind.OPAQUE);
    var binding =
        new LinkedJarBinding(
            Map.of(
                "create",
                new JavaBindingCallable(
                    "fixture.Foo",
                    "create",
                    "()Lfixture/Foo;",
                    JavaCallableKind.STATIC_METHOD,
                    List.of(),
                    reference)),
            Map.of(),
            Map.of());
    var released = new java.util.concurrent.atomic.AtomicBoolean();
    var assets = dev.w0fv1.norm.execution.RevisionResources.own(() -> released.set(true));
    var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    var creator =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    Object value;
                    try (var runtime =
                        JvmJarBindingRuntime.prepared(
                            List.of(binding),
                            List.of(output.jar(), output.classes()),
                            List.of(),
                            assets)) {
                      value =
                          ((dev.w0fv1.norm.execution.JarBindingResult.Reference)
                                  runtime.invoke("create", List.of()))
                              .value();
                    }
                    assets.close();
                    assertFalse(released.get());
                    assertEquals(42, value.getClass().getMethod("read").invoke(value));
                    java.lang.ref.Reference.reachabilityFence(value);
                  } catch (Throwable exception) {
                    failure.set(exception);
                  }
                });
    creator.join();
    if (failure.get() != null) throw new AssertionError(failure.get());
    assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(15),
        () -> {
          while (!released.get()) {
            System.gc();
            Thread.sleep(20);
          }
        });
  }

  @Test
  void incompatibleRetainedJavaValuesHaveAnExplicitMigrationBoundary() throws Exception {
    var first =
        compile(
            "old",
            Map.of(
                "fixture/Foo.java",
                "package fixture; public final class Foo { public static int read(Foo value) { return 1; } }"),
            Set.of("fixture/Foo.class"));
    var second =
        compile(
            "new",
            Map.of(
                "fixture/Foo.java",
                "package fixture; public final class Foo { public static int read(Foo value) { return 2; } }"),
            Set.of("fixture/Foo.class"));
    var reference = new JavaReferenceType("fixture.Foo", JavaReferenceKind.OPAQUE);
    var binding =
        new LinkedJarBinding(
            Map.of(
                "read",
                new JavaBindingCallable(
                    "fixture.Foo",
                    "read",
                    "(Lfixture/Foo;)I",
                    JavaCallableKind.STATIC_METHOD,
                    List.of(reference),
                    JavaPrimitiveType.INT)),
            Map.of(),
            Map.of());
    try (var initial = JvmJarBindingRuntime.prepared(List.of(), List.of(first.jar()), List.of());
        var candidate =
            JvmJarBindingRuntime.prepared(List.of(binding), List.of(second.jar()), List.of())) {
      Object value =
          initial
              .applicationClassLoader()
              .loadClass("fixture.Foo")
              .getDeclaredConstructor()
              .newInstance();
      var failure =
          assertThrows(
              JarBindingRuntimeException.class, () -> candidate.invoke("read", List.of(value)));
      assertEquals(RuntimeErrorCode.JAVA_MIGRATION_REQUIRED, failure.code());
      assertTrue(failure.getMessage().contains("fixture.Foo"));
      assertNull(failure.getCause());
    }
  }

  @Test
  void reciprocalJavaReferencesShareAComponentAcrossJarAndFacadeBoundaries() throws Exception {
    var output =
        compile(
            "coupled",
            Map.of(
                "fixture/Foo.java",
                    "package fixture; public final class Foo { public int read(Facade facade) { return facade.read(this); } }",
                "fixture/Facade.java",
                    "package fixture; public final class Facade { public int read(Foo value) { return 42; } }"),
            Set.of("fixture/Foo.class"));
    try (var runtime =
        JvmJarBindingRuntime.prepared(
            List.of(), List.of(output.jar(), output.classes()), List.of())) {
      Class<?> foo = runtime.applicationClassLoader().loadClass("fixture.Foo");
      Class<?> facade = runtime.applicationClassLoader().loadClass("fixture.Facade");
      assertSame(foo.getClassLoader(), facade.getClassLoader());
      assertEquals(
          42,
          foo.getMethod("read", facade)
              .invoke(
                  foo.getDeclaredConstructor().newInstance(),
                  facade.getDeclaredConstructor().newInstance()));
    }
  }

  @Test
  void packageAccessPreservesItsDefiningRuntimePackage() throws Exception {
    var output =
        compile(
            "package-access",
            Map.of(
                "fixture/Base.java",
                    "package fixture; public class Base { static int value() { return 9; } }",
                "fixture/Child.java",
                    "package fixture; public final class Child extends Base { public static int read() { return value(); } }"),
            Set.of());
    try (var runtime =
        JvmJarBindingRuntime.prepared(List.of(), List.of(output.classes()), List.of())) {
      Class<?> base = runtime.applicationClassLoader().loadClass("fixture.Base");
      Class<?> child = runtime.applicationClassLoader().loadClass("fixture.Child");
      assertSame(base.getClassLoader(), child.getClassLoader());
      assertEquals(9, child.getMethod("read").invoke(null));
    }
  }

  @Test
  void applicationResourcesRemainVisibleToTheJavaRealm() throws Exception {
    var output =
        compile(
            "resources",
            Map.of("fixture/Resource.java", "package fixture; public final class Resource {}"),
            Set.of());
    Path resource =
        output.classes.resolve("META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference");
    Files.createDirectories(resource);
    Path entry = resource.resolve("fixture.Resource");
    Files.writeString(entry, "fixture.Resource");
    try (var runtime =
        JvmJarBindingRuntime.prepared(List.of(), List.of(output.classes()), List.of())) {
      ClassLoader loader = runtime.applicationClassLoader();
      assertNotNull(
          loader.getResource("META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference"));
      assertNotNull(
          loader.getResource(
              "META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference/fixture.Resource"));
      assertTrue(
          java.util.Collections.list(
                  loader.getResources(
                      "META-INF/micronaut/io.micronaut.inject.BeanDefinitionReference"))
              .stream()
              .findAny()
              .isPresent());
    }
  }

  private record Output(Path classes, Path jar) {}

  private Output compile(String name, Map<String, String> sources, Set<String> jarClasses)
      throws Exception {
    Path root = Files.createDirectories(directory.resolve(name));
    Path classes = Files.createDirectories(root.resolve("classes"));
    var arguments =
        new java.util.ArrayList<String>(
            List.of(
                "-classpath",
                System.getProperty("norm.test.compilerJar"),
                "-d",
                classes.toString()));
    for (var source : new java.util.TreeMap<>(sources).entrySet()) {
      Path file = root.resolve(source.getKey());
      Files.createDirectories(file.getParent());
      Files.writeString(file, source.getValue());
      arguments.add(file.toString());
    }
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, arguments.toArray(String[]::new)));
    Path jar = root.resolve("dependency.jar");
    try (var archive = new JarOutputStream(Files.newOutputStream(jar))) {
      for (String entry : jarClasses.stream().sorted().toList()) {
        archive.putNextEntry(new JarEntry(entry));
        archive.write(Files.readAllBytes(classes.resolve(entry)));
        archive.closeEntry();
      }
    }
    return new Output(classes, jar);
  }
}
