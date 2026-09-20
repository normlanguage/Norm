package dev.w0fv1.norm.platform;

import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class CancellableDelay {
  private CancellableDelay() {}

  public static void await(BooleanSupplier cancellation, PlatformDuration duration)
      throws InterruptedException {
    var control = new OperationControl(cancellation, duration);
    do {
      if (control.isCancellationRequested() || Thread.currentThread().isInterrupted())
        throw new InterruptedException("delay cancelled");
      long remaining = control.remainingNanoseconds();
      if (remaining == 0) return;
      TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(20)));
    } while (true);
  }
}
