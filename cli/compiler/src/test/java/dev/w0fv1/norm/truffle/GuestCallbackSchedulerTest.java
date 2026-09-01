package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class GuestCallbackSchedulerTest {
  @Test
  @Timeout(5)
  void pumpsGuestCallbacksUntilCancellation() throws Exception {
    Thread owner = Thread.currentThread();
    AtomicReference<Object> result = new AtomicReference<>();
    try (GuestCallbackScheduler scheduler = new GuestCallbackScheduler()) {
      Thread callback =
          Thread.ofVirtual()
              .start(
                  () -> {
                    result.set(scheduler.invoke(() -> "handled"));
                    owner.interrupt();
                  });

      scheduler.runUntilCancellation();
      callback.join(2_000);

      assertEquals("handled", result.get());
    } finally {
      Thread.interrupted();
    }
  }
}
