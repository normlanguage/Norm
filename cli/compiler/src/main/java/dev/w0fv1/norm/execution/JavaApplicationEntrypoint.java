package dev.w0fv1.norm.execution;

@FunctionalInterface
public interface JavaApplicationEntrypoint {
  void execute(ClassLoader applicationClassLoader);
}
