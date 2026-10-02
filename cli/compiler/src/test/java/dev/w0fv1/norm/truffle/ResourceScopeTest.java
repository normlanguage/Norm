package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ResourceScopeTest {
  @Test
  void closesRemainingResourcesOnceInReverseRegistrationOrder() {
    List<String> closed = new ArrayList<>();
    ResourceScope scope = new ResourceScope();
    ManagedResource first = scope.register("first", () -> closed.add("first"));
    scope.register("second", () -> closed.add("second"));

    first.close();
    first.close();
    scope.close();
    scope.close();

    assertEquals(List.of("first", "second"), closed);
  }

  @Test
  void retainsLaterCloseFailuresAsSuppressedFailures() {
    ResourceScope scope = new ResourceScope();
    IllegalStateException first = new IllegalStateException("first");
    IllegalArgumentException second = new IllegalArgumentException("second");
    scope.register(
        "first",
        () -> {
          throw first;
        });
    scope.register(
        "second",
        () -> {
          throw second;
        });

    ResourceCloseException failure = assertThrows(ResourceCloseException.class, scope::close);

    assertSame(second, failure.getCause());
    assertEquals(1, failure.getSuppressed().length);
    assertSame(first, failure.getSuppressed()[0].getCause());
  }

  @Test
  void preservesTheFirstExplicitCloseFailure() {
    ResourceScope scope = new ResourceScope();
    ManagedResource resource =
        scope.register(
            "resource",
            () -> {
              throw new IllegalStateException("close");
            });

    ResourceCloseException first = assertThrows(ResourceCloseException.class, resource::close);
    ResourceCloseException second = assertThrows(ResourceCloseException.class, resource::close);

    assertSame(first, second);
  }

  @Test
  void reusesOneOwnerWhenTheSameHostResourceIsReturnedAgain() throws Exception {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AutoCloseable resource = closes::incrementAndGet;

    ManagedResource factoryResult = scope.register("factory", resource);
    ManagedResource listElement = scope.register("E", resource);

    assertSame(factoryResult, listElement);
    resource.close();
    listElement.closedExternally();
    assertSame(factoryResult, scope.register("E", resource));
    scope.close();
    assertEquals(1, closes.get());
  }

  @Test
  void scopeClosesAnUnreleasedFactoryAndCollectionAliasOnlyOnce() {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AutoCloseable resource = closes::incrementAndGet;

    assertSame(scope.register("factory", resource), scope.register("E", resource));
    scope.close();

    assertEquals(1, closes.get());
  }

  @Test
  void equalButDistinctResourcesRetainIndependentOwners() {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    class EqualResource implements AutoCloseable {
      @Override public boolean equals(Object other) { return other instanceof EqualResource; }
      @Override public int hashCode() { return 1; }
      @Override public void close() { closes.incrementAndGet(); }
    }
    EqualResource first = new EqualResource();
    EqualResource second = new EqualResource();

    ManagedResource firstOwner = scope.register("first", first);
    ManagedResource secondOwner = scope.register("second", second);

    org.junit.jupiter.api.Assertions.assertNotSame(firstOwner, secondOwner);
    scope.close();
    assertEquals(2, closes.get());
  }

  @Test
  void aNewGenericFactoryResultStillBelongsToTheScope() {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AutoCloseable first = closes::incrementAndGet;
    AutoCloseable second = closes::incrementAndGet;

    scope.register("T", first);
    scope.register("T", second);
    scope.close();

    assertEquals(2, closes.get());
  }
}
