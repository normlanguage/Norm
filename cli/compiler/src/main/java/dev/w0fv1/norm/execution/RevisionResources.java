package dev.w0fv1.norm.execution;

import java.lang.ref.Cleaner;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.collections4.map.AbstractReferenceMap.ReferenceStrength;
import org.apache.commons.collections4.map.ReferenceIdentityMap;

public final class RevisionResources {
  private static final Cleaner CLEANER = Cleaner.create();
  private final AutoCloseable resource;
  private final String identity;
  private final AtomicInteger references = new AtomicInteger(1);
  private final Map<Object, Boolean> values =
      new ReferenceIdentityMap<>(ReferenceStrength.WEAK, ReferenceStrength.HARD);

  private RevisionResources(String identity, AutoCloseable resource) {
    this.identity = identity;
    this.resource = Objects.requireNonNull(resource);
  }

  public static Lease own(AutoCloseable resource) {
    return own(null, resource);
  }

  public static Lease own(String identity, AutoCloseable resource) {
    return new Lease(new RevisionResources(identity, resource));
  }

  private Lease retain() {
    return acquire().orElseThrow(() -> new IllegalStateException("revision resources are retired"));
  }

  private java.util.Optional<Lease> acquire() {
    int current;
    do {
      current = references.get();
      if (current == 0) return java.util.Optional.empty();
    } while (!references.compareAndSet(current, Math.incrementExact(current)));
    return java.util.Optional.of(new Lease(this));
  }

  private synchronized boolean retain(Object value) {
    if (values.containsKey(value)) return false;
    Lease retained = retain();
    CLEANER.register(value, retained::close);
    values.put(value, Boolean.TRUE);
    return true;
  }

  private void release() {
    if (references.decrementAndGet() != 0) return;
    try {
      resource.close();
    } catch (RuntimeException | Error exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("Cannot release revision resources", exception);
    }
  }

  public static final class Lease implements AutoCloseable {
    private final RevisionResources owner;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Cleaner.Cleanable cleanup;

    private Lease(RevisionResources owner) {
      this.owner = owner;
      cleanup = CLEANER.register(this, owner::release);
    }

    public Lease retain() {
      if (closed.get()) throw new IllegalStateException("revision resource lease is closed");
      return owner.retain();
    }

    public java.util.Optional<String> identity() {
      return java.util.Optional.ofNullable(owner.identity);
    }

    public Source source() {
      return new Source(owner);
    }

    public boolean retain(Object value) {
      Objects.requireNonNull(value);
      if (closed.get()) throw new IllegalStateException("revision resource lease is closed");
      return owner.retain(value);
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) cleanup.clean();
    }
  }

  public static final class Source {
    private final RevisionResources owner;

    private Source(RevisionResources owner) {
      this.owner = owner;
    }

    public java.util.Optional<Lease> acquire() {
      return owner.acquire();
    }
  }
}
