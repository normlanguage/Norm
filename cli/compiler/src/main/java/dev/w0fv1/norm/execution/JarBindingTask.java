package dev.w0fv1.norm.execution;

public interface JarBindingTask extends AutoCloseable {
  java.util.concurrent.Executor continuationExecutor();

  java.util.Optional<java.util.concurrent.CompletionStage<Void>> ownedTermination();

  java.util.concurrent.CompletionStage<JarBindingResult> completion();

  JarBindingResult await();

  boolean cancel();

  boolean completed();

  Object hostValue();

  @Override
  void close();
}
