package dev.w0fv1.norm.bridge;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

final class JavaApplicationState {
  private static final ClassValue<Field> FIELDS =
      new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
          for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
              Field field = current.getDeclaredField(JavaApplicationObject.STATE_FIELD);
              if (!field.trySetAccessible())
                throw new IllegalStateException(
                    "Cannot access application state: " + type.getName());
              return field;
            } catch (NoSuchFieldException ignored) {
            }
          }
          throw new IllegalStateException("Missing application state: " + type.getName());
        }
      };

  private JavaApplicationState() {}

  static Object get(JavaApplicationObject object) {
    if (Proxy.isProxyClass(object.getClass())
        && Proxy.getInvocationHandler(object) instanceof JavaApplicationObject.StateHandler handler)
      return handler.state();
    try {
      return FIELDS.get(object.getClass()).get(object);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Cannot read application state", exception);
    }
  }

  static void set(JavaApplicationObject object, Object state) {
    if (Proxy.isProxyClass(object.getClass())
        && Proxy.getInvocationHandler(object)
            instanceof JavaApplicationObject.StateHandler handler) {
      handler.state(state);
      return;
    }
    try {
      FIELDS.get(object.getClass()).set(object, state);
    } catch (IllegalAccessException exception) {
      throw new IllegalStateException("Cannot write application state", exception);
    }
  }
}
