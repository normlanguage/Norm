package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.JarBindingResult;
import org.junit.jupiter.api.Test;

final class JavaValueAdapterTest {
  @Test
  void adaptsScalarNullAndCodePointArgumentsWithoutExecutionState() {
    assertNull(JavaValueAdapter.jarArgument(RuntimeValues.NullValue.INSTANCE, null, null));
    assertEquals(
        0x1F600,
        JavaValueAdapter.jarArgument(new RuntimeValues.CodePointValue(0x1F600), null, null));
    assertEquals("value", JavaValueAdapter.jarArgument("value", null, null));
    assertEquals(
        42,
        JavaValueAdapter.jarBindingValue(
            CoreType.INTEGER, new JarBindingResult.Scalar(42), null, null, null));
    assertSame(
        RuntimeValues.NullValue.INSTANCE,
        JavaValueAdapter.jarBindingValue(
            CoreType.STRING, JarBindingResult.Null.INSTANCE, null, null, null));
  }

  @Test
  void routingDoesNotOwnValuesOrExecutionResources() {
    assertTrue(
        java.util.Arrays.stream(IntrinsicDispatcher.class.getDeclaredMethods())
            .noneMatch(method -> method.getName().startsWith("jar")));
    for (var field : JavaValueAdapter.class.getDeclaredFields())
      assertTrue(java.lang.reflect.Modifier.isStatic(field.getModifiers()));
    for (var id : dev.w0fv1.norm.abi.IntrinsicId.values())
      assertNotNull(IntrinsicDispatcher.resolve(id), id.name());
  }
}
