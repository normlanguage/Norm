package dev.w0fv1.norm.execution;

import java.util.List;

@FunctionalInterface
public interface JarBindingCallback {
  default Object identity() {
    return this;
  }

  Object invoke(List<JarBindingResult> arguments);
}
