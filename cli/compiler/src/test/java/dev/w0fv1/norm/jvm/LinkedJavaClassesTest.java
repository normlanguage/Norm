package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LinkedJavaClassesTest {
  private static boolean initialized;

  public static final class InitializationProbe {
    static {
      initialized = true;
    }
  }

  @Test
  void resolvesImmutableClassesWithoutInitializingThem() {
    var number =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("sample.types", 1), "sample", "Number");
    var array =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("sample.types", 1), "sample", "Array");
    var mode =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("sample.types", 1), "sample", "Mode");
    var probe =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("sample.types", 1), "sample", "Probe");
    var binding =
        new LinkedJarBinding(
            Map.of(),
            Map.of(
                number,
                "I",
                array,
                "[[Ljava/lang/String;",
                mode,
                "Ljava/lang/Thread$State;",
                probe,
                "Ldev/w0fv1/norm/jvm/LinkedJavaClassesTest$InitializationProbe;"),
            Map.of(mode, Map.of("New", "NEW")));
    var classes = LinkedJavaClasses.resolve(List.of(binding), getClass().getClassLoader());
    assertSame(int.class, classes.classes().get(number));
    assertSame(String[][].class, classes.classes().get(array));
    assertSame(Thread.State.class, classes.classes().get(mode));
    assertSame(InitializationProbe.class, classes.classes().get(probe));
    assertFalse(initialized);
    assertEquals("NEW", classes.enumConstants().get(mode).get("New"));
    assertThrows(UnsupportedOperationException.class, () -> classes.classes().clear());
    assertThrows(
        UnsupportedOperationException.class, () -> classes.enumConstants().get(mode).clear());
  }

  @Test
  void rejectsDuplicateClassAndEnumMappings() {
    var type =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("sample.types", 1), "sample", "Mode");
    var binding =
        new LinkedJarBinding(Map.of(), Map.of(type, "Ljava/lang/Thread$State;"), Map.of());
    assertThrows(
        JarBindingRuntimeException.class,
        () -> LinkedJavaClasses.resolve(List.of(binding, binding), getClass().getClassLoader()));
    var enums = new LinkedJarBinding(Map.of(), Map.of(), Map.of(type, Map.of("New", "NEW")));
    assertThrows(
        JarBindingRuntimeException.class,
        () -> LinkedJavaClasses.resolve(List.of(enums, enums), getClass().getClassLoader()));
  }

  @Test
  void resolvesApplicationClassesPerExecutionWithoutMutatingTheLinkedTable() {
    var dynamic =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("sample.types", 1),
            "dev.w0fv1.norm.jvm",
            "LinkedJavaClassesTest$InitializationProbe");
    var classType = new JavaReferenceType("java.lang.Class", JavaReferenceKind.CLASS);
    var callable =
        new JavaBindingCallable(
            "sample.Identity",
            "identity",
            "(Ljava/lang/Class;)Ljava/lang/Class;",
            JavaCallableKind.STATIC_METHOD,
            List.of(classType),
            classType);
    var bindings = List.of(new LinkedJarBinding(Map.of("identity", callable), Map.of(), Map.of()));
    var linked = LinkedJavaClasses.resolve(bindings, getClass().getClassLoader());
    Map<String, dev.w0fv1.norm.bridge.JavaDirectCall> calls =
        Map.of("identity", arguments -> arguments[0]);
    for (int iteration = 0; iteration < 2; iteration++) {
      try (var runtime =
          JvmJarBindingRuntime.closedWorld(
              LinkedJarBinding.linkCalls(bindings), calls, linked, Map.of())) {
        assertEquals(
            new dev.w0fv1.norm.execution.JarBindingResult.ClassReference(List.of(dynamic)),
            runtime.invoke("identity", List.of(dynamic)));
      }
      assertFalse(linked.classes().containsKey(dynamic));
    }
    assertFalse(initialized);
  }

  @Test
  void rejectsInvalidOrUnavailableDeclaredTypes() {
    var type =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("sample.types", 1), "sample", "Missing");
    var missing =
        new LinkedJarBinding(Map.of(), Map.of(type, "Lsample/UnavailableLinkedType;"), Map.of());
    assertThrows(
        TypeNotPresentException.class,
        () -> LinkedJavaClasses.resolve(List.of(missing), getClass().getClassLoader()));
    var invalid = new LinkedJarBinding(Map.of(), Map.of(type, "not-a-descriptor"), Map.of());
    assertThrows(
        JarBindingRuntimeException.class,
        () -> LinkedJavaClasses.resolve(List.of(invalid), getClass().getClassLoader()));
  }
}
