package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.bridge.JavaApplicationObject;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class JavaFunctionShapeTest {
  @Test
  void adapterOwnsRuntimeStateAndPreservesFunctionalDefaultMethods() {
    Object state = new Object();
    Object adapter = JavaFunctionShape.FUNCTION.adapt(state, arguments -> arguments[0] + "!");
    var sidecar = assertInstanceOf(JavaApplicationObject.class, adapter);
    assertSame(state, JavaApplicationObject.state(sidecar));
    assertEquals("hello!", JavaFunctionShape.FUNCTION.invoke(adapter, new Object[] {"hello"}));
    @SuppressWarnings("unchecked")
    var function = (Function<Object, Object>) adapter;
    assertEquals("hello!?", function.andThen(value -> value + "?").apply("hello"));
    assertEquals(System.identityHashCode(adapter), adapter.hashCode());
    assertEquals(adapter, adapter);
    assertNotEquals(adapter, new Object());
    Object replacement = new Object();
    JavaApplicationObject.state(sidecar, replacement);
    assertSame(replacement, JavaApplicationObject.state(sidecar));
  }
}
