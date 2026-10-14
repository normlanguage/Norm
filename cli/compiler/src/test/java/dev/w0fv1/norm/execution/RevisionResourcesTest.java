package dev.w0fv1.norm.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RevisionResourcesTest {
  @Test
  void valueRetentionUsesIdentityAndSharesAcrossAllOwnerLeases() throws Exception {
    var closed = new AtomicInteger();
    var revision = RevisionResources.own(closed::incrementAndGet);
    var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
    var creator =
        Thread.ofVirtual()
            .start(
                () -> {
                  try (var other = revision.retain()) {
                    Object first = new String("equal");
                    Object second = new String("equal");
                    assertEquals(first, second);
                    assertNotSame(first, second);
                    assertTrue(revision.retain(first));
                    assertFalse(other.retain(first));
                    assertTrue(other.retain(second));
                    assertFalse(revision.retain(second));
                    revision.close();
                    assertEquals(0, closed.get());
                    java.lang.ref.Reference.reachabilityFence(first);
                    java.lang.ref.Reference.reachabilityFence(second);
                  } catch (Throwable exception) {
                    failure.set(exception);
                  }
                });
    creator.join();
    if (failure.get() != null) throw new AssertionError(failure.get());
    assertTimeoutPreemptively(
        java.time.Duration.ofSeconds(15),
        () -> {
          while (closed.get() == 0) {
            System.gc();
            Thread.sleep(20);
          }
        });
    assertEquals(1, closed.get());
    assertThrows(IllegalStateException.class, () -> revision.retain(new Object()));
  }

  @Test
  void concurrentRetentionRegistersTheSameValueOnce() throws Exception {
    var registrations = new AtomicInteger();
    Object value = new Object();
    try (var revision = RevisionResources.own(() -> {})) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var workers = new java.util.ArrayList<Thread>();
      for (int index = 0; index < 32; index++) {
        workers.add(
            Thread.ofVirtual()
                .start(
                    () -> {
                      try (var lease = revision.retain()) {
                        start.await();
                        if (lease.retain(value)) registrations.incrementAndGet();
                      } catch (InterruptedException exception) {
                        throw new AssertionError(exception);
                      }
                    }));
      }
      start.countDown();
      for (Thread worker : workers) worker.join();
      assertEquals(1, registrations.get());
      java.lang.ref.Reference.reachabilityFence(value);
    }
  }

  @Test
  void componentSourceSharesOnlyWhileAnActualOwnerIsAlive() {
    var closed = new AtomicInteger();
    var publication = RevisionResources.own(closed::incrementAndGet);
    var source = publication.source();
    var previous = publication.retain();
    publication.close();
    var candidate = source.acquire().orElseThrow();
    previous.close();
    assertEquals(0, closed.get());
    candidate.close();
    assertEquals(1, closed.get());
    assertTrue(source.acquire().isEmpty());
  }

  @Test
  void retainedValuesKeepResourcesAliveUntilTheirLastLeaseIsReleased() {
    var closed = new AtomicInteger();
    var revision = RevisionResources.own(closed::incrementAndGet);
    var frame = revision.retain();
    var value = frame.retain();
    revision.close();
    assertEquals(0, closed.get());
    frame.close();
    assertEquals(0, closed.get());
    value.close();
    assertEquals(1, closed.get());
    value.close();
    assertEquals(1, closed.get());
    assertThrows(IllegalStateException.class, value::retain);
  }

  @Test
  void independentRealmsRetireIndependently() {
    var retired = new AtomicInteger();
    var first = RevisionResources.own(retired::incrementAndGet);
    var second = RevisionResources.own(retired::incrementAndGet);
    var oldCallback = first.retain();
    first.close();
    second.close();
    assertEquals(1, retired.get());
    oldCallback.close();
    assertEquals(2, retired.get());
  }
}
