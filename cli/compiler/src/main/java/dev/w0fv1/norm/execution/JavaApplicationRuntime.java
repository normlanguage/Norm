package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.bridge.JavaDirectCall;

public interface JavaApplicationRuntime {
  ClassLoader applicationClassLoader();

  default java.util.List<ClassLoader> applicationClassLoaders() {
    return java.util.List.of(applicationClassLoader());
  }

  java.util.Map<String, JavaDirectCall> applicationCalls();

  java.util.Set<String> applicationTypes();

  boolean bindsEnum(JarBindingClassReference.Nominal type);
}
