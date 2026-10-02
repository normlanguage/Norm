package dev.w0fv1.norm.truffle;

import java.lang.ref.WeakReference;
import java.util.Objects;

final class ManagedResource implements AutoCloseable {
  private final ResourceScope scope;
  private final String name;
  private final WeakReference<AutoCloseable> reference;
  private AutoCloseable owned;
  private final WeakReference<Object> owner;
  private Runnable ownerRelease;
  private boolean transferred;
  private boolean closed;
  private ResourceCloseException failure;

  ManagedResource(ResourceScope scope, String name, AutoCloseable resource) {
    this(scope, name, resource, null);
  }

  ManagedResource(ResourceScope scope, String name, AutoCloseable resource, Object owner) {
    this.scope = Objects.requireNonNull(scope, "scope");
    this.name = Objects.requireNonNull(name, "name");
    Objects.requireNonNull(resource, "resource");
    this.owner = owner == null ? null : new WeakReference<>(owner);
    this.owned = owner == null ? resource : null;
    this.reference = new WeakReference<>(resource);
  }

  <T> T value(Class<T> type) {
    return type.cast(hostValue());
  }

  synchronized Object hostValue() {
    AutoCloseable resource = owned != null ? owned : reference.get();
    if (resource == null) throw new IllegalStateException("closed resource is unavailable");
    return resource;
  }

  Object borrowingOwner() {
    return owner == null ? null : owner.get();
  }

  synchronized void closedExternally() {
    if (closed) return;
    closed = true;
    scope.release(this);
    owned = null;
    releaseOwner();
  }

  synchronized void transferOwnership(Runnable accept, Runnable release) {
    if (owner != null || transferred) return;
    if (closed) throw new IllegalStateException("closed resource ownership cannot be transferred");
    try {
      accept.run();
      if (closed) throw new IllegalStateException("resource was closed while accepting ownership");
    } catch (RuntimeException | Error failure) {
      try {
        release.run();
      } catch (RuntimeException | Error releaseFailure) {
        failure.addSuppressed(releaseFailure);
      }
      try {
        close();
      } catch (RuntimeException | Error closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
    scope.release(this);
    ownerRelease = release;
    transferred = true;
  }

  @Override
  public synchronized void close() {
    if (owner != null)
      throw new IllegalStateException("borrowed resource must be closed by its owner");
    if (closed) {
      if (failure != null) throw failure;
      return;
    }
    closed = true;
    scope.release(this);
    AutoCloseable resource = owned;
    try {
      resource.close();
    } catch (Exception | Error exception) {
      failure = new ResourceCloseException(name, exception);
      throw failure;
    } finally {
      owned = null;
      releaseOwner();
    }
    if (failure != null) throw failure;
  }

  private void releaseOwner() {
    if (ownerRelease != null) {
      Runnable release = ownerRelease;
      ownerRelease = null;
      try {
        release.run();
      } catch (RuntimeException | Error releaseFailure) {
        if (failure == null) failure = new ResourceCloseException(name, releaseFailure);
        else failure.addSuppressed(releaseFailure);
      }
    }
    if (failure != null) throw failure;
  }
}
