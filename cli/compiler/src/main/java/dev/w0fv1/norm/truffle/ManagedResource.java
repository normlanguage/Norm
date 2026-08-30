package dev.w0fv1.norm.truffle;

import java.util.Objects;

final class ManagedResource implements AutoCloseable {
  private final ResourceScope scope;
  private final String name;
  private final AutoCloseable resource;
  private boolean closed;
  private ResourceCloseException failure;

  ManagedResource(ResourceScope scope, String name, AutoCloseable resource) {
    this.scope = Objects.requireNonNull(scope, "scope");
    this.name = Objects.requireNonNull(name, "name");
    this.resource = Objects.requireNonNull(resource, "resource");
  }

  <T> T value(Class<T> type) {
    return type.cast(resource);
  }

  @Override
  public synchronized void close() {
    if (closed) {
      if (failure != null) throw failure;
      return;
    }
    closed = true;
    scope.release(this);
    try {
      resource.close();
    } catch (Exception exception) {
      failure = new ResourceCloseException(name, exception);
      throw failure;
    }
  }
}
