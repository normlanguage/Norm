package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.execution.JarBindingResult;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class LinkedJarBindingTest {
  @Test
  void linksImmutableCallsAndExecutesThemWithoutBindingDescriptions() {
    var callable =
        new JavaBindingCallable(
            "java.lang.String",
            "length",
            "()I",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            JavaPrimitiveType.INT);
    var bindings = List.of(new LinkedJarBinding(Map.of("length", callable), Map.of(), Map.of()));
    var calls = LinkedJarBinding.linkCalls(bindings);
    assertSame(callable, calls.get("length"));
    assertThrows(UnsupportedOperationException.class, calls::clear);
    var classes = LinkedJavaClasses.resolve(bindings, getClass().getClassLoader());
    for (int iteration = 0; iteration < 2; iteration++) {
      try (var runtime =
          JvmJarBindingRuntime.closedWorld(
              calls,
              Map.of("length", arguments -> ((String) arguments[0]).length()),
              classes,
              Map.of())) {
        assertEquals(new JarBindingResult.Scalar(4), runtime.invoke("length", List.of("Norm")));
      }
      assertEquals(1, calls.size());
    }
  }

  @Test
  void rejectsDuplicateCallsEvenWhenTheyHaveTheSameTarget() {
    var callable =
        new JavaBindingCallable(
            "java.lang.String",
            "length",
            "()I",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            JavaPrimitiveType.INT);
    var binding = new LinkedJarBinding(Map.of("length", callable), Map.of(), Map.of());
    assertThrows(
        JarBindingRuntimeException.class,
        () -> LinkedJarBinding.linkCalls(List.of(binding, binding)));
    assertThrows(
        JarBindingRuntimeException.class,
        () -> JvmJarBindingRuntime.closedWorld(List.of(binding, binding)));
    assertEquals(Map.of(), LinkedJarBinding.linkCalls(List.of()));
  }
}
