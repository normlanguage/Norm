package dev.w0fv1.norm.bridge;

public interface JavaApplicationObject {
  String STATE_FIELD = "$normState";

  static Object state(JavaApplicationObject object) {
    return JavaApplicationState.get(object);
  }

  static void state(JavaApplicationObject object, Object state) {
    JavaApplicationState.set(object, state);
  }

  interface StateHandler extends java.lang.reflect.InvocationHandler {
    Object state();

    void state(Object value);
  }
}
