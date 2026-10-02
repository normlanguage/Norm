package dev.w0fv1.norm.truffle;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ResourceScope implements AutoCloseable {
  private final Deque<ManagedResource> resources = new ArrayDeque<>();
  private final ReferenceQueue<AutoCloseable> reclaimed = new ReferenceQueue<>();
  private final Map<IdentityReference, ManagedResource> identities = new HashMap<>();
  private boolean closed;

  synchronized ManagedResource register(String name, AutoCloseable resource) {
    if (closed) throw new IllegalStateException("resource scope is closed");
    Objects.requireNonNull(resource, "resource");
    IdentityReference collected;
    while ((collected = (IdentityReference) reclaimed.poll()) != null) identities.remove(collected);
    ManagedResource existing = identities.get(new IdentityReference(resource));
    if (existing != null) return existing;
    ManagedResource managed = new ManagedResource(this, name, resource);
    resources.addLast(managed);
    identities.put(new IdentityReference(resource, reclaimed), managed);
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
      identities.clear();
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

  private static final class IdentityReference extends WeakReference<AutoCloseable> {
    private final int hash;

    IdentityReference(AutoCloseable resource) {
      super(resource);
      hash = System.identityHashCode(resource);
    }

    IdentityReference(AutoCloseable resource, ReferenceQueue<AutoCloseable> queue) {
      super(resource, queue);
      hash = System.identityHashCode(resource);
    }

    @Override
    public int hashCode() {
      return hash;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) return true;
      AutoCloseable value = get();
      return other instanceof IdentityReference reference
          && value != null
          && value == reference.get();
    }
  }
}
