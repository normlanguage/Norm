package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.bridge.JavaDirectCall;

public interface JavaApplicationRuntime {
  ClassLoader applicationClassLoader();

  java.util.Map<String, JavaDirectCall> applicationCalls();
}
