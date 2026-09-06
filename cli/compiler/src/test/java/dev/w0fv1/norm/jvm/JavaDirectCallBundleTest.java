package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.bridge.JavaDirectCall;
import dev.w0fv1.norm.bridge.JavaDirectCallRegistry;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaDirectCallBundleTest {
  @TempDir Path directory;

  @Test
  void exportsOnlyTheSharedBridgeProtocolToGeneratedClasses() throws Exception {
    Path classes =
        Path.of(JavaDirectCall.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (var input = java.nio.file.Files.newInputStream(classes.resolve("module-info.class"))) {
      var descriptor = java.lang.module.ModuleDescriptor.read(input);
      assertEquals("dev.w0fv1.norm", descriptor.name());
      var exported =
          descriptor.exports().stream()
              .filter(value -> !value.isQualified())
              .map(java.lang.module.ModuleDescriptor.Exports::source)
              .toList();
      assertTrue(exported.contains(JavaDirectCall.class.getPackageName()));
      assertTrue(exported.contains(JavaDirectCallRegistry.class.getPackageName()));
      assertFalse(exported.contains("dev.w0fv1.norm.jvm"));
    }
  }

  @Test
  void preparingGeneratedCallsDoesNotInitializeTheirApplicationTargets() throws Throwable {
    Path source = directory.resolve("DeferredTarget.java");
    java.nio.file.Files.writeString(
        source,
        """
        public class DeferredTarget {
          static { State.count++; }
          public static int value() { return State.count; }
          public static class State { public static int count; }
        }
        """);
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", directory.toString(), source.toString()));
    try (var loader =
        new URLClassLoader(
            new java.net.URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      var counter = loader.loadClass("DeferredTarget$State").getField("count");
      var target =
          new JavaBindingCallable(
              "DeferredTarget",
              "value",
              "()I",
              JavaCallableKind.STATIC_METHOD,
              List.of(),
              JavaPrimitiveType.INT);
      String registryName = JavaApplicationMethodIndex.REGISTRY_NAME;
      new JavaDirectCallBundle().write(registryName, Map.of("value", target), directory, loader);
      assertEquals(0, counter.getInt(null));
      var calls = JavaApplicationCallLinker.link(loader);
      assertEquals(0, counter.getInt(null));
      assertEquals(1, calls.get("value").invoke(new Object[0]));
      assertEquals(1, counter.getInt(null));
      assertEquals(1, calls.get("value").invoke(new Object[0]));
      assertEquals(1, counter.getInt(null));
    }
  }

  @Test
  void writesApplicationTargetsWithAnIndependentRegistryIdentity() throws Throwable {
    String name = "sample.ApplicationCalls";
    var target = new JavaApplicationMethodIndex.Target("java.lang.String", "length", "()I");
    new JavaDirectCallBundle()
        .write(name, Map.of("method", target), directory, getClass().getClassLoader());
    try (var loader =
        new URLClassLoader(
            new java.net.URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      var registry = (JavaDirectCallRegistry) loader.loadClass(name).getConstructor().newInstance();
      assertEquals(4, registry.calls().get("method").invoke(new Object[] {"Norm"}));
      assertFalse(
          java.nio.file.Files.exists(
              directory.resolve(JavaDirectCallBundle.REGISTRY_NAME.replace('.', '/') + ".class")));
    }
  }

  @Test
  void largeRegistriesDoNotExceedJvmMethodLimits() throws Throwable {
    var parse =
        new JavaBindingCallable(
            "java.lang.Integer",
            "parseInt",
            "(Ljava/lang/String;)I",
            JavaCallableKind.STATIC_METHOD,
            List.of(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)),
            JavaPrimitiveType.INT);
    var calls = new java.util.LinkedHashMap<String, JavaBindingCallable>();
    for (int index = 0; index < 5000; index++) calls.put("parse" + index, parse);
    new JavaDirectCallBundle()
        .write(
            List.of(new LinkedJarBinding(calls, Map.of(), Map.of())),
            directory,
            getClass().getClassLoader());
    try (var loader =
        new URLClassLoader(
            new java.net.URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      var registry =
          (JavaDirectCallRegistry)
              loader.loadClass(JavaDirectCallBundle.REGISTRY_NAME).getConstructor().newInstance();
      var linked = registry.calls();
      assertEquals(5000, linked.size());
      assertEquals(42, linked.get("parse4999").invoke(new Object[] {"42"}));
    }
  }

  @Test
  void generatedRegistryLoadsAndInvokesWithoutMethodLookup() throws Throwable {
    var parse =
        new JavaBindingCallable(
            "java.lang.Integer",
            "parseInt",
            "(Ljava/lang/String;)I",
            JavaCallableKind.STATIC_METHOD,
            List.of(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)),
            JavaPrimitiveType.INT);
    var binding = new LinkedJarBinding(Map.of("parse", parse), Map.of(), Map.of());
    new JavaDirectCallBundle().write(List.of(binding), directory, getClass().getClassLoader());
    try (var loader =
        new URLClassLoader(
            new java.net.URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      var registry =
          (JavaDirectCallRegistry)
              loader.loadClass(JavaDirectCallBundle.REGISTRY_NAME).getConstructor().newInstance();
      assertEquals(java.util.Set.of("parse"), registry.calls().keySet());
      assertEquals(42, registry.calls().get("parse").invoke(new Object[] {"42"}));
      try (var runtime = JvmJarBindingRuntime.closedWorld(List.of(binding), registry.calls())) {
        assertEquals(
            new dev.w0fv1.norm.execution.JarBindingResult.Scalar(21),
            runtime.invoke("parse", List.of("21")));
      }
    }
  }
}
