package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaCompilationCacheTest {
  @TempDir Path directory;

  @Test
  void restoresExecutableClassesAndInvalidatesChangedSourcesAndClasspath() throws Exception {
    var stub =
        new JavaAnnotationStub(
            "Sample",
            "public class Sample { public static String value() { return \"cached\"; } }");
    Path source = directory.resolve("Sample.java");
    Files.writeString(source, stub.source());
    Path classes = Files.createDirectories(directory.resolve("classes"));
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-proc:none", "-d", classes.toString(), source.toString()));
    Path javac =
        Path.of(
            System.getProperty("java.home"),
            "bin",
            System.getProperty("os.name").startsWith("Windows") ? "javac.exe" : "javac");
    Path dependency = directory.resolve("dependency.bin");
    Files.writeString(dependency, "first");
    var key = JavaCompilationCache.key(List.of(stub), List.of(dependency), javac);
    Path storage = directory.resolve("cache");
    new JavaCompilationCache(storage).write(key, classes);
    Path restored = Files.createDirectories(directory.resolve("restored"));
    assertTrue(new JavaCompilationCache(storage).restore(key, restored));
    try (var loader =
        new java.net.URLClassLoader(
            new java.net.URL[] {restored.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
      assertEquals("cached", loader.loadClass("Sample").getMethod("value").invoke(null));
    }
    assertNotEquals(
        key,
        JavaCompilationCache.key(
            List.of(new JavaAnnotationStub("Sample", stub.source().replace("cached", "changed"))),
            List.of(dependency),
            javac));
    Files.writeString(dependency, "second");
    assertNotEquals(key, JavaCompilationCache.key(List.of(stub), List.of(dependency), javac));
  }
}
