package dev.w0fv1.norm.application;

import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionRevision;
import dev.w0fv1.norm.execution.LiveExecution;
import dev.w0fv1.norm.execution.LiveExecutionStatus;
import dev.w0fv1.norm.project.ProjectInputSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ApplicationSession implements AutoCloseable {
  private final Object gate = new Object();
  private final SourceApplicationCompiler compiler;
  private final java.util.Optional<dev.w0fv1.norm.core.CompilationResult> compilation;
  private final LiveExecution execution;
  private final LiveApplicationSession updates;
  private final dev.w0fv1.norm.execution.ExecutionContext context;
  private final Consumer<String> progress;
  private final AtomicBoolean cancellation;
  private final CountDownLatch finished = new CountDownLatch(1);
  private boolean started;
  private boolean closed;
  private Thread runner;

  ApplicationSession(
      ExecutionBackend backend,
      SourceApplicationCompiler compiler,
      java.util.Optional<dev.w0fv1.norm.core.CompilationResult> compilation,
      ExecutionRevision revision,
      ProjectInputSnapshot inputs,
      AtomicBoolean cancellation,
      Consumer<String> progress,
      Consumer<String> diagnostics)
      throws IOException {
    this.compiler = compiler;
    this.compilation = compilation;
    this.cancellation = cancellation;
    this.progress = progress;
    if (revision == null) {
      context = null;
      execution = null;
      updates = null;
      return;
    }
    context = revision.context();
    try {
      execution = backend.prepareLive(revision);
    } catch (RuntimeException | Error exception) {
      revision.close();
      throw exception;
    }
    try {
      updates = new LiveApplicationSession(inputs, execution, compiler, progress, diagnostics);
    } catch (IOException | RuntimeException | Error exception) {
      execution.close();
      throw exception;
    }
  }

  public ApplicationRunResult execute() throws IOException {
    synchronized (gate) {
      if (closed || started) throw new IllegalStateException("application session cannot start");
      started = true;
      runner = Thread.currentThread();
    }
    try {
      if (execution != null) {
        updates.start();
        progress.accept("Starting application");
        execution.execute(context);
      }
      return compilation
          .map(result -> new ApplicationRunResult(result.isSuccess(), result.diagnostics()))
          .orElseGet(() -> new ApplicationRunResult(true, List.of()));
    } finally {
      try {
        if (updates != null) updates.close();
      } finally {
        synchronized (gate) {
          runner = null;
        }
        finished.countDown();
      }
    }
  }

  public LiveExecutionStatus status() {
    return execution == null ? new LiveExecutionStatus(0, List.of()) : execution.status();
  }

  java.util.Optional<dev.w0fv1.norm.core.CompilationResult> compilation() {
    return compilation;
  }

  public Batch beginBatch() {
    if (updates == null) throw new IllegalStateException("application compilation failed");
    return new Batch(updates.beginBatch());
  }

  public CompletableFuture<ApplicationUpdateResult> requestUpdate() {
    if (updates == null) throw new IllegalStateException("application compilation failed");
    return updates.requestUpdate();
  }

  public void cancel() {
    cancellation.set(true);
  }

  public final class Batch implements AutoCloseable {
    private final long identity;
    private final AtomicBoolean completed = new AtomicBoolean();

    private Batch(long identity) {
      this.identity = identity;
    }

    public CompletableFuture<ApplicationUpdateResult> commit() {
      if (!completed.compareAndSet(false, true)) throw new IllegalStateException("batch is closed");
      return updates.commitBatch(identity);
    }

    @Override
    public void close() {
      if (completed.compareAndSet(false, true)) updates.closeBatch(identity);
    }
  }

  @Override
  public void close() throws IOException {
    Thread active;
    synchronized (gate) {
      if (closed) return;
      active = runner;
      if (active == Thread.currentThread())
        throw new IllegalStateException("Cancel the active application before closing its session");
      closed = true;
    }
    cancel();
    boolean interrupted = Thread.interrupted();
    try {
      if (active != null) {
        boolean complete = false;
        while (!complete) {
          try {
            finished.await();
            complete = true;
          } catch (InterruptedException exception) {
            interrupted = true;
          }
        }
      }
      try {
        if (updates != null) updates.close();
      } finally {
        try {
          if (execution != null) execution.close();
        } finally {
          compiler.close();
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
  }
}
