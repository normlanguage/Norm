package dev.w0fv1.norm.workspace;

import dev.w0fv1.norm.frontend.CancellationToken;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AnalysisScheduler<K> implements AutoCloseable {
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Map<K, Slot> slots = new HashMap<>();
  private boolean closed;

  @FunctionalInterface
  public interface Analysis {
    void run(CancellationToken cancellation) throws Exception;
  }

  public CompletableFuture<Void> submit(K key, Analysis analysis) {
    var job = new Job(analysis);
    java.util.List<CompletableFuture<Void>> cancelled;
    synchronized (this) {
      if (closed) throw new IllegalStateException("analysis scheduler is closed");
      var slot = slots.get(key);
      boolean start = slot == null;
      if (start) {
        slot = new Slot();
        slots.put(key, slot);
      }
      cancelled = slot.cancel();
      slot.pending = job;
      if (start) {
        Slot selected = slot;
        executor.execute(() -> run(key, selected));
      }
    }
    cancelled.forEach(completion -> completion.cancel(false));
    return job.completion;
  }

  public synchronized CompletableFuture<Void> settled() {
    return CompletableFuture.allOf(
        slots.values().stream().map(slot -> slot.drained).toArray(CompletableFuture[]::new));
  }

  public void cancel(K key) {
    java.util.List<CompletableFuture<Void>> cancelled;
    synchronized (this) {
      var slot = slots.get(key);
      if (slot == null) return;
      cancelled = slot.cancel();
    }
    cancelled.forEach(completion -> completion.cancel(false));
  }

  private void run(K key, Slot slot) {
    while (true) {
      Job job;
      synchronized (this) {
        job = slot.pending;
        slot.pending = null;
        slot.active = job;
        if (job == null) {
          slots.remove(key, slot);
        }
      }
      if (job == null) {
        slot.drained.complete(null);
        return;
      }
      try {
        if (!job.cancelled) job.analysis.run(() -> job.cancelled);
        job.completion.complete(null);
      } catch (Throwable failure) {
        job.completion.completeExceptionally(failure);
      } finally {
        synchronized (this) {
          slot.active = null;
        }
      }
    }
  }

  @Override
  public void close() {
    var cancelled = new java.util.ArrayList<CompletableFuture<Void>>();
    synchronized (this) {
      if (closed) return;
      closed = true;
      slots.values().forEach(slot -> cancelled.addAll(slot.cancel()));
    }
    cancelled.forEach(completion -> completion.cancel(false));
    executor.shutdownNow();
    executor.close();
  }

  private static final class Job {
    private final Analysis analysis;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private volatile boolean cancelled;

    private Job(Analysis analysis) {
      this.analysis = java.util.Objects.requireNonNull(analysis, "analysis");
    }
  }

  private static final class Slot {
    private Job active;
    private Job pending;
    private final CompletableFuture<Void> drained = new CompletableFuture<>();

    private java.util.List<CompletableFuture<Void>> cancel() {
      var cancelled = new java.util.ArrayList<CompletableFuture<Void>>();
      if (active != null) {
        active.cancelled = true;
        cancelled.add(active.completion);
      }
      if (pending != null) {
        pending.cancelled = true;
        cancelled.add(pending.completion);
      }
      return cancelled;
    }
  }
}
