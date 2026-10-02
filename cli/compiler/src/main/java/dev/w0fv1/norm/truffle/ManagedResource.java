package dev.w0fv1.norm.truffle;

import java.lang.ref.WeakReference;
import java.util.Objects;

final class ManagedResource implements AutoCloseable {
  private final ResourceScope scope;
  private final String name;
  private final WeakReference<AutoCloseable> reference;
  private AutoCloseable owned;
  private boolean closed;
  private ResourceCloseException failure;

  ManagedResource(ResourceScope scope, String name, AutoCloseable resource) {
    this.scope = Objects.requireNonNull(scope, "scope");
    this.name = Objects.requireNonNull(name, "name");
    this.owned = Objects.requireNonNull(resource, "resource");
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

  synchronized void closedExternally() {
    if (closed) return;
    closed = true;
    scope.release(this);
    owned = null;
  }

  @Override
  public synchronized void close() {
    if (closed) {
      if (failure != null) throw failure;
      return;
    }
    closed = true;
    scope.release(this);
    AutoCloseable resource = owned;
    try {
      resource.close();
    } catch (Exception exception) {
      failure = new ResourceCloseException(name, exception);
      throw failure;
    } finally {
      owned = null;
    }
  }
}
