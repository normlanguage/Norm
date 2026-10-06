package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaApplicationCallLinkerTest {
  @TempDir Path directory;

  @Test
  void applicationsWithoutGeneratedTypesHaveNoHostCalls() {
    assertEquals(
        dev.w0fv1.norm.execution.JavaApplicationLinkage.EMPTY,
        JavaApplicationCallLinker.link(getClass().getClassLoader()));
    try (var runtime = new JvmJarBindingRuntime(List.of())) {
      assertEquals(Map.of(), runtime.applicationCalls());
    }
  }

  @Test
  void linksTheGeneratedRegistryAndSharesTheSameContractWithBothRuntimes() throws Throwable {
    new JavaDirectCallBundle()
        .writeApplication(
            Map.of(
                "length",
                new JavaApplicationMethodIndex.Target("java.lang.String", "length", "()I")),
            java.util.Set.of("java.lang.String"),
            directory,
            getClass().getClassLoader());
    dev.w0fv1.norm.execution.JavaApplicationLinkage linked;
    try (var loader =
        new URLClassLoader(
            new java.net.URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      linked = JavaApplicationCallLinker.link(loader);
      assertEquals(4, linked.calls().get("length").invoke(new Object[] {"Norm"}));
      assertThrows(UnsupportedOperationException.class, () -> linked.calls().clear());
      assertEquals(java.util.Set.of("java.lang.String"), linked.types());
      assertThrows(UnsupportedOperationException.class, () -> linked.types().clear());
      var classes = LinkedJavaClasses.resolve(List.of(), loader);
      for (int attempt = 0; attempt < 2; attempt++) {
        try (var runtime = JvmJarBindingRuntime.closedWorld(Map.of(), Map.of(), classes, linked)) {
          assertEquals(linked.types(), runtime.applicationTypes());
          assertSame(linked.calls().get("length"), runtime.applicationCalls().get("length"));
          assertEquals(4, runtime.applicationCalls().get("length").invoke(new Object[] {"Norm"}));
        }
        assertEquals(1, linked.calls().size());
      }
    }
    var runtime = new JvmJarBindingRuntime(List.of(), List.of(directory));
    try {
      assertEquals(4, runtime.applicationCalls().get("length").invoke(new Object[] {"Norm"}));
      assertThrows(UnsupportedOperationException.class, () -> runtime.applicationCalls().clear());
    } finally {
      runtime.close();
    }
    assertThrows(JarBindingRuntimeException.class, runtime::applicationCalls);
  }

  @Test
  void malformedRegistriesAreNotTreatedAsApplicationsWithoutCalls() throws Exception {
    Path source = directory.resolve("Calls.java");
    Files.writeString(
        source, "package dev.w0fv1.norm.generated.application; public class Calls {}");
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", directory.toString(), source.toString()));
    try (var loader =
        new URLClassLoader(
            new java.net.URL[] {directory.toUri().toURL()}, getClass().getClassLoader())) {
      var failure =
          assertThrows(
              JarBindingRuntimeException.class, () -> JavaApplicationCallLinker.link(loader));
      assertTrue(failure.getMessage().contains(JavaApplicationMethodIndex.REGISTRY_NAME));
      assertInstanceOf(ClassCastException.class, failure.getCause());
    }
  }
}
