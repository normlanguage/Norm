package dev.w0fv1.norm.truffle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

final class ResourceScope implements AutoCloseable {
  private final Deque<ManagedResource> resources = new ArrayDeque<>();
  private boolean closed;

  synchronized ManagedResource register(String name, AutoCloseable resource) {
    if (closed) throw new IllegalStateException("resource scope is closed");
    ManagedResource managed =
        new ManagedResource(this, name, Objects.requireNonNull(resource, "resource"));
    resources.addLast(managed);
    return managed;
  }

  synchronized void release(ManagedResource resource) {
    resources.remove(resource);
  }

  @Override
  public void close() {
    List<ManagedResource> remaining = new ArrayList<>();
    synchronized (this) {
      if (closed) return;
      closed = true;
      while (!resources.isEmpty()) remaining.add(resources.removeLast());
    }
    ResourceCloseException failure = null;
    for (ManagedResource resource : remaining) {
      try {
        resource.close();
      } catch (ResourceCloseException exception) {
        if (failure == null) failure = exception;
        else failure.addSuppressed(exception);
      }
    }
    if (failure != null) throw failure;
  }
}
