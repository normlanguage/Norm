package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.bridge.JavaApplicationResource;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ApplicationClassLoaderIsolationTest {
  @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

  public interface ParentService {}

  public static class ParentProvider implements ParentService {}

  @Test
  void isolatesDependenciesWhenTheCompilerRunsAsANamedModule() throws Exception {
    var source = directory.resolve("IsolationProbe.java");
    java.nio.file.Files.writeString(
        source,
        """
        import dev.w0fv1.norm.jvm.JvmJarBindingRuntime;
        import java.util.List;

        class IsolationProbe {
          public static void main(String[] args) throws Exception {
            var compilerType = ClassLoader.getPlatformClassLoader().loadClass("com.google.gson.Gson");
            if (!compilerType.getModule().isNamed()) throw new AssertionError("Compiler dependency is not modular");
            try (var runtime = new JvmJarBindingRuntime(List.of())) {
              try {
                runtime.applicationClassLoader().loadClass("com.google.gson.Gson");
                throw new AssertionError("Compiler dependency leaked into the application");
              } catch (ClassNotFoundException expected) {
              }
              if (runtime.applicationClassLoader().getResource("com/google/gson/Gson.class") != null) {
                throw new AssertionError("Compiler resource leaked into the application");
              }
            }
          }
        }
        """);
    var log = directory.resolve("probe.log");
    var javaExecutable =
        java.nio.file.Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
    var process =
        new ProcessBuilder(
                javaExecutable.toString(),
                "--module-path",
                System.getProperty("norm.test.modulePath"),
                "--add-modules=dev.w0fv1.norm",
                "--add-exports=dev.w0fv1.norm/dev.w0fv1.norm.jvm=ALL-UNNAMED",
                source.toString())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    try {
      assertTrue(process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(0, process.exitValue(), java.nio.file.Files.readString(log));
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  @Test
  void exposesPlatformAndBridgeTypesWithoutLeakingCompilerDependencies() throws Exception {
    try (var runtime = new JvmJarBindingRuntime(List.of())) {
      var loader = runtime.applicationClassLoader();
      assertSame(
          javax.xml.parsers.DocumentBuilderFactory.class,
          loader.loadClass("javax.xml.parsers.DocumentBuilderFactory"));
      assertSame(
          JavaApplicationResource.class, loader.loadClass(JavaApplicationResource.class.getName()));
      assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.google.gson.Gson"));
      var resource = "com/google/gson/Gson.class";
      assertNotNull(getClass().getClassLoader().getResource(resource));
      assertNull(loader.getResource(resource));
      assertFalse(loader.getResources(resource).hasMoreElements());
    }
  }

  @Test
  void doesNotDiscoverCompilerServiceProviders() throws Exception {
    String service =
        "META-INF/services/dev.w0fv1.norm.jvm.ApplicationClassLoaderIsolationTest$ParentService";
    assertNotNull(getClass().getClassLoader().getResource(service));
    assertTrue(
        java.util.ServiceLoader.load(ParentService.class, getClass().getClassLoader())
            .findFirst()
            .isPresent());
    try (var runtime = new JvmJarBindingRuntime(List.of())) {
      assertNull(runtime.applicationClassLoader().getResource(service));
      assertFalse(runtime.applicationClassLoader().getResources(service).hasMoreElements());
      assertFalse(
          java.util.ServiceLoader.load(ParentService.class, runtime.applicationClassLoader())
              .findFirst()
              .isPresent());
    }
  }
}
