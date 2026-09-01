package dev.w0fv1.norm.execution;

import java.util.List;

@FunctionalInterface
public interface JarBindingCallback {
  Object invoke(List<JarBindingResult> arguments);
}
