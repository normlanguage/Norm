package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.bridge.JavaDirectCall;
import dev.w0fv1.norm.bridge.NormApplicationMethod;
import dev.w0fv1.norm.core.DefinitionId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaApplicationMethodIndexTest {
  private static final String ID =
      "0000000000000000000000000000000000000000000000000000000000000000:0";
  @TempDir Path directory;

  public interface Generic<T> {
    @NormApplicationMethod(ID)
    T hello(T name);
  }

  public static class Sample implements Generic<String> {
    @NormApplicationMethod("0000000000000000000000000000000000000000000000000000000000000000:2")
    public Sample() {}

    @NormApplicationMethod(ID)
    public String hello(String name) {
      return name;
    }

    public String hello(int number) {
      return Integer.toString(number);
    }

    @NormApplicationMethod("0000000000000000000000000000000000000000000000000000000000000000:1")
    public static void utility() {}
  }

  public static class Conflict {
    @NormApplicationMethod(ID)
    public void different() {}
  }

  @Test
  void rejectsConflictingDefinitionTargets() throws Exception {
    var stubs = new java.util.ArrayList<JavaAnnotationStub>();
    for (var type : List.of(Sample.class, Conflict.class)) {
      String resource = type.getName().replace('.', '/') + ".class";
      Path file = directory.resolve(resource);
      Files.createDirectories(file.getParent());
      try (var input = getClass().getResourceAsStream("/" + resource)) {
        Files.copy(input, file);
      }
      stubs.add(new JavaAnnotationStub(type.getName(), ""));
    }
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> JavaApplicationMethodIndex.analyze(directory, stubs));
    assertTrue(failure.getMessage().contains(ID));
    assertTrue(failure.getMessage().contains("different"));
  }

  @Test
  void derivesOnlyAnnotatedInstanceTargetsFromCompiledOutput() throws Throwable {
    assertNull(
        Sample.class.getMethod("hello", String.class).getAnnotation(NormApplicationMethod.class));
    String binaryName = Sample.class.getName();
    String resource = binaryName.replace('.', '/') + ".class";
    Path file = directory.resolve(resource);
    Files.createDirectories(file.getParent());
    try (var input = getClass().getResourceAsStream("/" + resource)) {
      Files.copy(input, file);
    }
    var analysis =
        JavaApplicationMethodIndex.analyze(
            directory, List.of(new JavaAnnotationStub(binaryName, "")));
    var calls = analysis.instanceMethods();
    assertEquals(
        java.util.Set.of(
            DefinitionId.parse(ID),
            DefinitionId.parse(
                "0000000000000000000000000000000000000000000000000000000000000000:1"),
            DefinitionId.parse(
                "0000000000000000000000000000000000000000000000000000000000000000:2")),
        analysis.entryPoints());
    assertThrows(UnsupportedOperationException.class, analysis.entryPoints()::clear);
    assertEquals(1, calls.size());
    var target = calls.get(DefinitionId.parse(ID));
    assertEquals(binaryName, target.owner());
    assertEquals("hello", target.name());
    assertEquals("(Ljava/lang/String;)Ljava/lang/String;", target.descriptor());
    assertEquals(JavaCallableKind.INSTANCE_METHOD, target.kind());
    assertThrows(UnsupportedOperationException.class, calls::clear);
    String generatedName = "sample.IndexedCall";
    byte[] generated =
        new JavaDirectCallGenerator().generate(generatedName, target, getClass().getClassLoader());
    var loader =
        new ClassLoader(getClass().getClassLoader()) {
          Class<?> define() {
            return defineClass(generatedName, generated, 0, generated.length);
          }
        };
    var call = (JavaDirectCall) loader.define().getConstructor().newInstance();
    assertEquals("Norm", call.invoke(new Object[] {new Sample(), "Norm"}));
  }

  @Test
  void abstractMethodsProvideHostDispatchButNotExecutableEntryPoints() throws Exception {
    String binaryName = Generic.class.getName();
    String resource = binaryName.replace('.', '/') + ".class";
    Path file = directory.resolve(resource);
    Files.createDirectories(file.getParent());
    try (var input = getClass().getResourceAsStream("/" + resource)) {
      Files.copy(input, file);
    }
    var analysis =
        JavaApplicationMethodIndex.analyze(
            directory, List.of(new JavaAnnotationStub(binaryName, "")));
    assertEquals(java.util.Set.of(DefinitionId.parse(ID)), analysis.instanceMethods().keySet());
    assertTrue(analysis.entryPoints().isEmpty());
  }

  @Test
  void rejectsMissingCompiledDeclarations() {
    assertThrows(
        java.io.IOException.class,
        () ->
            JavaApplicationMethodIndex.analyze(
                directory, List.of(new JavaAnnotationStub("missing.Type", ""))));
  }
}
