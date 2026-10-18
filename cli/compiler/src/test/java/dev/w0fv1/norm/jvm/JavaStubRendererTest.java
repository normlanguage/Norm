package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.bridge.JavaApplicationObject;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaStubRendererTest {
  @TempDir Path directory;

  @Test
  void generatedObjectOwnsHiddenStateWithoutRetainingUnreachableCycles() throws Exception {
    var type =
        new JavaStubPlan.Type(
            "fixture.Generated",
            "fixture",
            "Generated",
            JavaStubPlan.TypeKind.CLASS,
            false,
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false,
            false,
            Optional.empty());
    var source = new JavaStubRenderer().render(new JavaStubPlan(List.of(type))).getFirst();
    Path file = directory.resolve("Generated.java");
    Files.writeString(file, source.source());
    Path classes = Files.createDirectories(directory.resolve("classes"));
    assertEquals(
        0,
        javax.tools.ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                null,
                "-classpath",
                System.getProperty("norm.test.compilerJar"),
                "-d",
                classes.toString(),
                file.toString()));
    var valueReference = new AtomicReference<WeakReference<Object>>();
    var stateReference = new AtomicReference<WeakReference<Object>>();
    var failure = new AtomicReference<Throwable>();
    var creator =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (var loader =
                      new URLClassLoader(
                          new URL[] {classes.toUri().toURL()}, getClass().getClassLoader())) {
                    Object host =
                        loader
                            .loadClass("fixture.Generated")
                            .getDeclaredConstructor()
                            .newInstance();
                    var generated = assertInstanceOf(JavaApplicationObject.class, host);
                    Object state = new AtomicReference<>(host);
                    generated.$normState(state);
                    System.gc();
                    assertSame(state, generated.$normState());
                    assertTrue(
                        java.lang.reflect.Modifier.isTransient(
                            host.getClass().getDeclaredField("$normState").getModifiers()));
                    valueReference.set(new WeakReference<>(host));
                    stateReference.set(new WeakReference<>(state));
                  } catch (Throwable exception) {
                    failure.set(exception);
                  }
                });
    creator.join();
    if (failure.get() != null) throw new AssertionError(failure.get());
    assertTimeoutPreemptively(
        Duration.ofSeconds(15),
        () -> {
          while (valueReference.get().get() != null || stateReference.get().get() != null) {
            System.gc();
            Thread.sleep(50);
          }
        });
  }
}
