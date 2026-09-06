package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.bridge.JavaDirectCall;
import java.util.List;
import org.junit.jupiter.api.Test;

final class JavaDirectCallGeneratorTest {
  public interface Greeting {
    default String greet(String name) {
      return "Hello " + name;
    }
  }

  public static class Parent implements Greeting {}

  public static class Proxy extends Parent {
    @Override
    public String greet(String name) {
      return "intercepted " + super.greet(name);
    }
  }

  record ApplicationTarget(String owner, String name, String descriptor, JavaCallableKind kind)
      implements JavaCallTarget {}

  @Test
  void dispatchesApplicationTargetsThroughOverridesAndInterfaceDefaults() throws Throwable {
    var signature = "(Ljava/lang/String;)Ljava/lang/String;";
    var interfaceCall =
        target(
            new ApplicationTarget(
                Greeting.class.getName(), "greet", signature, JavaCallableKind.INSTANCE_METHOD));
    var inheritedCall =
        target(
            new ApplicationTarget(
                Parent.class.getName(), "greet", signature, JavaCallableKind.INSTANCE_METHOD));
    assertEquals("Hello Norm", interfaceCall.invoke(new Object[] {new Parent(), "Norm"}));
    assertEquals(
        "intercepted Hello Norm", interfaceCall.invoke(new Object[] {new Proxy(), "Norm"}));
    assertEquals(
        "intercepted Hello Norm", inheritedCall.invoke(new Object[] {new Proxy(), "Norm"}));
  }

  public static class Sample {
    public int value;
    public static long shared;

    public Sample(int value) {
      this.value = value;
    }

    public int add(int amount) {
      return value + amount;
    }

    public static void fail() {
      throw new IllegalArgumentException("direct");
    }
  }

  @Test
  void invokesConstructorsMethodsFieldsAndInterfaces() throws Throwable {
    String owner = Sample.class.getName();
    Sample instance =
        (Sample)
            target(owner, "<init>", "(I)V", JavaCallableKind.CONSTRUCTOR).invoke(new Object[] {4});
    assertEquals(
        7,
        target(owner, "add", "(I)I", JavaCallableKind.INSTANCE_METHOD)
            .invoke(new Object[] {instance, 3}));
    assertNull(
        target(owner, "value", "I", JavaCallableKind.INSTANCE_FIELD_SET)
            .invoke(new Object[] {instance, 9}));
    assertEquals(
        9,
        target(owner, "value", "I", JavaCallableKind.INSTANCE_FIELD_GET)
            .invoke(new Object[] {instance}));
    target(owner, "shared", "J", JavaCallableKind.STATIC_FIELD_SET).invoke(new Object[] {12L});
    assertEquals(
        12L, target(owner, "shared", "J", JavaCallableKind.STATIC_FIELD_GET).invoke(new Object[0]));
    assertEquals(
        2,
        target("java.util.List", "size", "()I", JavaCallableKind.INSTANCE_METHOD)
            .invoke(new Object[] {List.of("a", "b")}));
    assertEquals(
        42,
        target(
                "java.lang.Integer",
                "parseInt",
                "(Ljava/lang/String;)I",
                JavaCallableKind.STATIC_METHOD)
            .invoke(new Object[] {"42"}));
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                target(owner, "fail", "()V", JavaCallableKind.STATIC_METHOD).invoke(new Object[0]));
    assertEquals("direct", failure.getMessage());
  }

  @Test
  void supportsPrimitiveAndReferenceArrays() throws Throwable {
    for (String descriptor : List.of("[I", "[J", "[Z", "[D", "[Ljava/lang/String;", "[[I")) {
      Object array =
          target(descriptor, "new", "", JavaCallableKind.ARRAY_CONSTRUCTOR)
              .invoke(new Object[] {2});
      Object value =
          switch (descriptor) {
            case "[I" -> 3;
            case "[J" -> 4L;
            case "[Z" -> true;
            case "[D" -> 2.5;
            case "[Ljava/lang/String;" -> "text";
            default -> new int[] {5};
          };
      assertNull(
          target(descriptor, "set", "", JavaCallableKind.ARRAY_SET)
              .invoke(new Object[] {array, 1, value}));
      assertEquals(
          value,
          target(descriptor, "get", "", JavaCallableKind.ARRAY_GET)
              .invoke(new Object[] {array, 1}));
      assertEquals(
          2,
          target(descriptor, "length", "", JavaCallableKind.ARRAY_LENGTH)
              .invoke(new Object[] {array}));
    }
  }

  @Test
  void directCallsReuseRuntimeConversionAndExceptionSemantics() throws Exception {
    var call =
        new JavaBindingCallable(
            "java.lang.Integer",
            "parseInt",
            "(Ljava/lang/String;)I",
            JavaCallableKind.STATIC_METHOD,
            List.of(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)),
            JavaPrimitiveType.INT);
    var binding =
        new LinkedJarBinding(
            java.util.Map.of("parse", call), java.util.Map.of(), java.util.Map.of());
    try (var runtime =
        JvmJarBindingRuntime.closedWorld(
            List.of(binding), java.util.Map.of("parse", target(call)))) {
      assertEquals(
          new dev.w0fv1.norm.execution.JarBindingResult.Scalar(42),
          runtime.invoke("parse", List.of("42")));
      var failure =
          assertThrows(
              dev.w0fv1.norm.execution.JarBindingInvocationException.class,
              () -> runtime.invoke("parse", List.of("invalid")));
      assertInstanceOf(NumberFormatException.class, failure.getCause());
    }
    assertThrows(
        IllegalArgumentException.class,
        () -> JvmJarBindingRuntime.closedWorld(List.of(binding), java.util.Map.of()));
  }

  private static JavaDirectCall target(
      String owner, String method, String descriptor, JavaCallableKind kind) throws Exception {
    var callable =
        new JavaBindingCallable(owner, method, descriptor, kind, List.of(), JavaPrimitiveType.VOID);
    return target(callable);
  }

  private static JavaDirectCall target(JavaCallTarget callable) throws Exception {
    String name = "sample.generated.DirectCall";
    byte[] bytes =
        new JavaDirectCallGenerator()
            .generate(name, callable, JavaDirectCallGeneratorTest.class.getClassLoader());
    class Loader extends ClassLoader {
      Loader() {
        super(JavaDirectCallGeneratorTest.class.getClassLoader());
      }

      Class<?> define() {
        return defineClass(name, bytes, 0, bytes.length);
      }
    }
    return (JavaDirectCall) new Loader().define().getConstructor().newInstance();
  }
}
