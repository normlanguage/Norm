package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.bridge.JavaDirectCall;
import dev.w0fv1.norm.bridge.JavaDirectCallRegistry;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import java.util.Map;
import java.util.Objects;

public final class JavaApplicationCallLinker {
  private JavaApplicationCallLinker() {}

  public static Map<String, JavaDirectCall> link(ClassLoader loader) {
    Objects.requireNonNull(loader, "loader");
    Class<?> type;
    try {
      type = Class.forName(JavaApplicationMethodIndex.REGISTRY_NAME, false, loader);
    } catch (ClassNotFoundException absent) {
      return Map.of();
    }
    try {
      var registry = type.asSubclass(JavaDirectCallRegistry.class).getConstructor().newInstance();
      return Map.copyOf(registry.calls());
    } catch (ReflectiveOperationException | ClassCastException exception) {
      throw new JarBindingRuntimeException(
          "Cannot link generated application calls: " + type.getName(), exception);
    }
  }
}
