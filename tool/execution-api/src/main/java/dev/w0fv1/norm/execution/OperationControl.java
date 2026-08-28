package dev.w0fv1.norm.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class OperationControl {
  private final BooleanSupplier cancellation;
  private final PlatformDuration timeout;
  private final long startedNanoseconds;
  private final long timeoutNanoseconds;

  public OperationControl(BooleanSupplier cancellation, PlatformDuration timeout) {
    this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    startedNanoseconds = System.nanoTime();
    long value;
    try {
      value = Duration.ofSeconds(timeout.seconds(), timeout.nanoseconds()).toNanos();
    } catch (ArithmeticException ignored) {
      value = Long.MAX_VALUE;
    }
    timeoutNanoseconds = value;
  }

  public boolean isCancellationRequested() {
    return cancellation.getAsBoolean();
  }

  public PlatformDuration timeout() {
    return timeout;
  }

  public long remainingNanoseconds() {
    return Math.max(0, timeoutNanoseconds - (System.nanoTime() - startedNanoseconds));
  }

  public boolean hasTimedOut() {
    return remainingNanoseconds() == 0;
  }
}
