package dev.w0fv1.norm.execution;

public interface JarBindingTask extends AutoCloseable {
  JarBindingResult await();

  boolean cancel();

  boolean completed();

  Object hostValue();

  @Override
  void close();
}
