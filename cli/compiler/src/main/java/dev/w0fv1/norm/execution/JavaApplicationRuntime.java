package dev.w0fv1.norm.execution;

public interface JavaApplicationRuntime {
  ClassLoader applicationClassLoader();

  java.util.Map<String, dev.w0fv1.norm.bridge.JavaDirectCall> applicationCalls();
}
