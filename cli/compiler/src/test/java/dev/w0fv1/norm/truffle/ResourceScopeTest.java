package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class ResourceScopeTest {
  @Test
  void activeBorrowedViewsRetainTheirOwnerWithoutPinningItInTheExecutionRegistry()
      throws Exception {
    ResourceScope scope = new ResourceScope();
    Object owner = new Object();
    var reference = new java.lang.ref.WeakReference<>(owner);
    AutoCloseable child = () -> {};
    ManagedResource record = scope.borrow("child", child, owner);
    var view =
        new RuntimeValues.OpaqueResource(dev.w0fv1.norm.core.CoreType.STRING, record, "child");
    owner = null;
    for (int attempt = 0; attempt < 10; attempt++) {
      System.gc();
      Thread.sleep(10);
    }
    assertNotNull(reference.get());
    assertSame(reference.get(), view.borrowingOwner);
    view = null;
    for (int attempt = 0; attempt < 100 && reference.get() != null; attempt++) {
      System.gc();
      Thread.sleep(10);
    }
    assertNull(reference.get());
    assertNull(record.borrowingOwner());
    scope.close();
  }

  @Test
  void externallyClosedTransferredResourcesReleaseTheirOwnerWithoutClosingAgain() throws Exception {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AtomicInteger released = new AtomicInteger();
    AutoCloseable host = closes::incrementAndGet;
    ManagedResource resource = scope.register("host", host);
    resource.transferOwnership(() -> {}, released::incrementAndGet);
    host.close();
    resource.closedExternally();
    resource.closedExternally();
    scope.close();
    assertEquals(1, closes.get());
    assertEquals(1, released.get());
  }

  @Test
  void borrowingAnExistingOwnedResourcePreservesTheCanonicalOwner() {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AutoCloseable host = closes::incrementAndGet;
    ManagedResource owned = scope.register("factory", host);
    Object owner = new Object();
    assertSame(owned, scope.borrow("getter", host, owner));
    var borrowed =
        new RuntimeValues.OpaqueResource(
            dev.w0fv1.norm.core.CoreType.STRING, owned, "getter", null, owner);
    var owningAlias =
        new RuntimeValues.OpaqueResource(dev.w0fv1.norm.core.CoreType.STRING, owned, "factory");
    assertThrows(
        IllegalStateException.class,
        () ->
            IoIntrinsicDispatcher.resolve(dev.w0fv1.norm.abi.IntrinsicId.RESOURCE_CLOSE)
                .execute(null, new Object[] {borrowed}, null, null, null));
    IoIntrinsicDispatcher.resolve(dev.w0fv1.norm.abi.IntrinsicId.RESOURCE_TRANSFER_OWNERSHIP)
        .execute(null, new Object[] {borrowed, null, null}, null, null, null);
    org.junit.jupiter.api.Assertions.assertTrue(RuntimeValues.equal(borrowed, owningAlias));
    assertEquals(RuntimeValues.hash(borrowed), RuntimeValues.hash(owningAlias));
    assertEquals(0, closes.get());
    scope.close();
    assertEquals(1, closes.get());
  }

  @Test
  void borrowedAliasAfterOwnerCloseNeverAcquiresASecondCloseObligation() {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AutoCloseable host = closes::incrementAndGet;
    AutoCloseable parent = host::close;
    ManagedResource owner = scope.register("parent", parent);
    ManagedResource borrowed = scope.borrow("child", host, owner);
    owner.close();
    assertSame(borrowed, scope.register("alias", host));
    scope.close();
    assertEquals(1, closes.get());
  }

  @Test
  void rejectingAnOwnerReleasesRegistrationAndClosesExactlyOnce() {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AtomicInteger releases = new AtomicInteger();
    ManagedResource resource = scope.register("host", closes::incrementAndGet);
    IllegalStateException rejected = new IllegalStateException("rejected");
    assertSame(
        rejected,
        assertThrows(
            IllegalStateException.class,
            () ->
                resource.transferOwnership(
                    () -> {
                      throw rejected;
                    },
                    releases::incrementAndGet)));
    resource.close();
    scope.close();
    assertEquals(1, closes.get());
    assertEquals(1, releases.get());
  }

  @Test
  void transferredAliasesNeverRegisterWithAnotherOwnerAndExplicitCloseReleasesTheOwner() {
    ResourceScope scope = new ResourceScope();
    AtomicInteger accepted = new AtomicInteger();
    AtomicInteger released = new AtomicInteger();
    AtomicInteger secondOwner = new AtomicInteger();
    ManagedResource resource = scope.register("host", () -> {});
    resource.transferOwnership(accepted::incrementAndGet, released::incrementAndGet);
    resource.transferOwnership(secondOwner::incrementAndGet, () -> {});
    resource.close();
    resource.close();
    scope.close();
    assertEquals(1, accepted.get());
    assertEquals(1, released.get());
    assertEquals(0, secondOwner.get());
  }

  @Test
  void borrowedChildAndGenericAliasesRemainWithTheirOwner() {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AutoCloseable child = closes::incrementAndGet;
    AutoCloseable host = child::close;
    ManagedResource parent = scope.register("parent", host);
    ManagedResource borrowed = scope.borrow("child", child, parent);
    assertSame(borrowed, scope.register("E", child));
    assertThrows(IllegalStateException.class, borrowed::close);
    AtomicInteger registrations = new AtomicInteger();
    borrowed.transferOwnership(registrations::incrementAndGet, () -> {});
    assertEquals(0, registrations.get());
    scope.close();
    assertEquals(1, closes.get());
  }

  @Test
  void aliasesAfterExplicitFailureRetainTheSameFailure() {
    ResourceScope scope = new ResourceScope();
    AutoCloseable host =
        () -> {
          throw new IllegalStateException("host close");
        };
    ManagedResource first = scope.register("first", host);
    ResourceCloseException failure = assertThrows(ResourceCloseException.class, first::close);
    ManagedResource alias = scope.register("alias", host);
    assertSame(first, alias);
    assertSame(failure, assertThrows(ResourceCloseException.class, alias::close));
    scope.close();
  }

  @Test
  void sameHostInSeparateScopesHasSeparateOwnership() {
    ResourceScope first = new ResourceScope();
    ResourceScope second = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AutoCloseable host = closes::incrementAndGet;
    org.junit.jupiter.api.Assertions.assertNotSame(
        first.register("host", host), second.register("host", host));
    first.close();
    second.close();
    assertEquals(2, closes.get());
  }

  @Test
  void transferredOwnershipRemainsClosableByItsNewOwner() {
    ResourceScope scope = new ResourceScope();
    AtomicInteger closes = new AtomicInteger();
    AutoCloseable host = closes::incrementAndGet;
    ManagedResource resource = scope.register("host", host);
    resource.transferOwnership(() -> {}, () -> {});
    assertSame(resource, scope.register("alias", host));
    scope.close();
    assertEquals(0, closes.get());
    resource.close();
    resource.close();
    assertEquals(1, closes.get());
  }

  @Test
  void liveGuestHandleRetainsAReleasedHostUntilTheHandleIsDiscarded() throws Exception {
    ResourceScope scope = new ResourceScope();
    AutoCloseable host = new java.io.ByteArrayInputStream(new byte[0]);
    var reference = new java.lang.ref.WeakReference<>(host);
    var handle =
        new RuntimeValues.OpaqueResource(
            dev.w0fv1.norm.core.CoreType.STRING, scope.register("host", host), "host");
    host = null;
    handle.closedExternally();
    for (int i = 0; i < 10; i++) {
      System.gc();
      Thread.sleep(10);
    }
    assertNotNull(reference.get());
    assertSame(reference.get(), handle.hostValue());
    handle.resource.close();
    handle = null;
    for (int i = 0; i < 100 && reference.get() != null; i++) {
      System.gc();
      Thread.sleep(10);
    }
    assertNull(reference.get());
    scope.close();
  }

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
      @Override
      public boolean equals(Object other) {
        return other instanceof EqualResource;
      }

      @Override
      public int hashCode() {
        return 1;
      }

      @Override
      public void close() {
        closes.incrementAndGet();
      }
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
