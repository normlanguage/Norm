package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.bridge.JavaApplicationRegistry;
import dev.w0fv1.norm.execution.JarBindingRuntimeException;
import dev.w0fv1.norm.execution.JavaApplicationLinkage;
import java.util.Objects;

public final class JavaApplicationCallLinker {
  private JavaApplicationCallLinker() {}

  public static JavaApplicationLinkage link(ClassLoader loader) {
    Objects.requireNonNull(loader, "loader");
    Class<?> type;
    try {
      type = Class.forName(JavaApplicationMethodIndex.REGISTRY_NAME, false, loader);
    } catch (ClassNotFoundException absent) {
      return JavaApplicationLinkage.EMPTY;
    }
    try {
      var registry = type.asSubclass(JavaApplicationRegistry.class).getConstructor().newInstance();
      if (registry.format() != JavaApplicationRegistry.FORMAT)
        throw new JarBindingRuntimeException(
            "Unsupported application registry format: " + registry.format());
      return new JavaApplicationLinkage(registry.calls(), registry.types());
    } catch (ReflectiveOperationException | ClassCastException exception) {
      throw new JarBindingRuntimeException(
          "Cannot link generated application calls: " + type.getName(), exception);
    }
  }
}
