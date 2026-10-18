package dev.w0fv1.norm.execution;

import java.time.Duration;
import java.util.Optional;

public interface ExecutionUpdateSubscription extends AutoCloseable {
  Optional<ExecutionUpdateEvent> poll();

  Optional<ExecutionUpdateEvent> next(Duration timeout) throws InterruptedException;

  @Override
  void close();
}
