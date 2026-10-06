package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.JarBindingResult;
import org.junit.jupiter.api.Test;

final class JavaValueAdapterTest {
  @Test
  void adaptsJavaCharacterResultsToTheLanguageCodePointRepresentation() {
    Object value =
        JavaValueAdapter.jarBindingValue(
            CoreType.CODE_POINT, new JarBindingResult.Scalar((int) 'A'), null, null, null);
    assertEquals('A', assertInstanceOf(RuntimeValues.CodePointValue.class, value).value());
  }

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
  void usesTheSameScalarRepresentationForApplicationAndJarCalls() {
    Object codePoint = JavaValueAdapter.scalarValue(CoreType.CODE_POINT, 'A');
    assertEquals(65, JavaValueAdapter.hostValue(codePoint));
    assertEquals(
        JavaValueAdapter.scalarValue(CoreType.INTEGER, 7L),
        JavaValueAdapter.jarBindingValue(
            CoreType.INTEGER, new JarBindingResult.Scalar(7L), null, null, null));
    assertEquals(7, JavaValueAdapter.scalarValue(CoreType.INTEGER, 7L));
    assertSame(
        RuntimeValues.NullValue.INSTANCE, JavaValueAdapter.scalarValue(CoreType.STRING, null));
    assertEquals(7, JavaValueAdapter.scalarValue(CoreType.ANY, (byte) 7));
    assertEquals(
        'A',
        assertInstanceOf(
                RuntimeValues.CodePointValue.class, JavaValueAdapter.scalarValue(CoreType.ANY, 'A'))
            .value());
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
