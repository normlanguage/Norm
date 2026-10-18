package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.ExecutionUpdateEvent;
import dev.w0fv1.norm.execution.ExecutionUpdateEventKind;
import dev.w0fv1.norm.execution.ExecutionUpdateResult;
import dev.w0fv1.norm.execution.ExecutionUpdateSubscription;
import dev.w0fv1.norm.execution.LiveExecution;
import dev.w0fv1.norm.execution.LiveExecutionStatus;
import dev.w0fv1.norm.execution.RuntimeChangeAnalyzer;
import dev.w0fv1.norm.execution.RuntimeChangeSet;
import dev.w0fv1.norm.project.ProjectInputSnapshot;
import dev.w0fv1.norm.project.ProjectLoadException;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class LiveApplicationSession implements AutoCloseable {
  @FunctionalInterface
  interface CandidateCompiler {
    ApplicationCandidate compile() throws IOException;
  }

  private record Request(CompletableFuture<ExecutionUpdateResult> result, boolean force) {}

  private final Object gate = new Object();
  private final CandidateCompiler compiler;
  private final LiveExecution execution;
  private final Consumer<String> diagnostics;
  private final Consumer<String> progress;
  private final InputChangeMonitor monitor;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final ArrayDeque<Request> requests = new ArrayDeque<>();
  private final ArrayDeque<ExecutionUpdateEvent> eventHistory = new ArrayDeque<>();
  private final java.util.Set<EventSubscription> subscriptions = new java.util.HashSet<>();
  private long eventSequence;
  private CoreArtifact publishedArtifact;
  private Long batch;
  private long epoch;
  private ProjectInputSnapshot inputs;
  private ProjectInputSnapshot completedInputs;
  private Thread worker;
  private LiveExecutionStatus status;
  private volatile ExecutionUpdateResult latest;

  LiveApplicationSession(
      ProjectInputSnapshot inputs,
      CoreArtifact publishedArtifact,
      LiveExecution execution,
      CandidateCompiler compiler,
      Consumer<String> progress,
      Consumer<String> diagnostics)
      throws IOException {
    this.compiler = compiler;
    this.inputs = inputs;
    this.publishedArtifact =
        java.util.Objects.requireNonNull(publishedArtifact, "publishedArtifact");
    completedInputs = inputs;
    this.execution = execution;
    this.progress = progress;
    this.diagnostics = diagnostics;
    status = execution.status();
    monitor = new InputChangeMonitor();
    try {
      monitor.update(inputs);
    } catch (IOException | RuntimeException exception) {
      monitor.close();
      throw exception;
    }
  }

  LiveExecutionStatus status() {
    return execution.status();
  }

  Optional<ExecutionUpdateResult> latest() {
    return Optional.ofNullable(latest);
  }

  ExecutionUpdateSubscription subscribe(long afterSequence) {
    if (afterSequence < 0) throw new IllegalArgumentException("afterSequence must not be negative");
    synchronized (gate) {
      if (closed.get()) throw new IllegalStateException("update coordinator is closed");
      var subscription = new EventSubscription();
      eventHistory.stream()
          .filter(event -> event.sequence() > afterSequence)
          .forEach(subscription.events::offer);
      subscriptions.add(subscription);
      return subscription;
    }
  }

  private void emit(
      ExecutionUpdateEventKind kind,
      Optional<ExecutionUpdateResult> result,
      LiveExecutionStatus current) {
    synchronized (gate) {
      if (closed.get()) return;
      var event = new ExecutionUpdateEvent(++eventSequence, kind, current, result);
      eventHistory.addLast(event);
      while (eventHistory.size() > 256) eventHistory.removeFirst();
      subscriptions.forEach(subscription -> subscription.events.offer(event));
    }
  }

  private void emitResult(ExecutionUpdateResult result) {
    emit(
        switch (result.outcome()) {
          case PUBLISHED -> ExecutionUpdateEventKind.PUBLISHED;
          case UNCHANGED -> ExecutionUpdateEventKind.UNCHANGED;
          case REJECTED -> ExecutionUpdateEventKind.REJECTED;
          case FAILED -> ExecutionUpdateEventKind.FAILED;
        },
        Optional.of(result),
        result.status());
    if (!result.status().pendingLocations().isEmpty())
      emit(ExecutionUpdateEventKind.ADOPTION_PENDING, Optional.of(result), result.status());
  }

  private final class EventSubscription implements ExecutionUpdateSubscription {
    private final LinkedBlockingQueue<ExecutionUpdateEvent> events = new LinkedBlockingQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    @Override
    public Optional<ExecutionUpdateEvent> poll() {
      return Optional.ofNullable(events.poll());
    }

    @Override
    public Optional<ExecutionUpdateEvent> next(Duration timeout) throws InterruptedException {
      if (timeout.isNegative()) throw new IllegalArgumentException("timeout must not be negative");
      if (closed.get()) return Optional.empty();
      var event = events.poll(timeout.toNanos(), TimeUnit.NANOSECONDS);
      return closed.get() ? Optional.empty() : Optional.ofNullable(event);
    }

    @Override
    public void close() {
      if (!closed.compareAndSet(false, true)) return;
      synchronized (gate) {
        subscriptions.remove(this);
      }
      events.clear();
    }
  }

  dev.w0fv1.norm.execution.ExecutionUpdates.Batch beginBatch() {
    long identity;
    synchronized (gate) {
      if (closed.get()) throw new IllegalStateException("update coordinator is closed");
      if (batch != null) throw new IllegalStateException("A source update batch is already open");
      identity = ++epoch;
      batch = identity;
    }
    return new dev.w0fv1.norm.execution.ExecutionUpdates.Batch() {
      private final AtomicBoolean completed = new AtomicBoolean();

      @Override
      public CompletableFuture<ExecutionUpdateResult> commit() {
        if (!completed.compareAndSet(false, true))
          throw new IllegalStateException("batch is closed");
        return commitBatch(identity);
      }

      @Override
      public void close() {
        if (completed.compareAndSet(false, true)) closeBatch(identity);
      }
    };
  }

  private CompletableFuture<ExecutionUpdateResult> commitBatch(long identity) {
    synchronized (gate) {
      if (batch == null || batch != identity) throw new IllegalStateException("batch is closed");
      batch = null;
      return enqueueUpdate(false);
    }
  }

  private void closeBatch(long identity) {
    synchronized (gate) {
      if (batch == null || batch != identity) return;
      batch = null;
      epoch++;
      requests.addLast(new Request(null, false));
    }
  }

  CompletableFuture<ExecutionUpdateResult> requestUpdate() {
    return enqueueUpdate(true);
  }

  private CompletableFuture<ExecutionUpdateResult> enqueueUpdate(boolean force) {
    synchronized (gate) {
      if (closed.get()) throw new IllegalStateException("update coordinator is closed");
      if (batch != null)
        throw new IllegalStateException(
            "Commit or close the open source update batch before requesting publication");
      epoch++;
      var result = new CompletableFuture<ExecutionUpdateResult>();
      requests.addLast(new Request(result, force));
      emit(ExecutionUpdateEventKind.REQUESTED, Optional.empty(), execution.status());
      return result;
    }
  }

  void start() {
    synchronized (gate) {
      if (!inputs.matches()) requests.add(new Request(null, true));
    }
    worker =
        Thread.ofVirtual()
            .name("norm-live-compilation")
            .start(
                () -> {
                  while (!closed.get()) {
                    List<Request> captured = List.of();
                    ExecutionUpdateResult completed = null;
                    try {
                      var current = execution.status();
                      if (!status.equals(current)) {
                        boolean wasPending = !status.pendingLocations().isEmpty();
                        if (!current.pendingLocations().isEmpty())
                          diagnostics.accept(
                              "info[NORM-LIVE-0003]: Runtime revision "
                                  + current.revision()
                                  + " is pending for active locations: "
                                  + String.join(", ", current.pendingLocations()));
                        else if (!status.pendingLocations().isEmpty())
                          progress.accept("Completed pending runtime transitions");
                        status = current;
                        if (wasPending && current.pendingLocations().isEmpty())
                          emit(
                              ExecutionUpdateEventKind.ADOPTED,
                              Optional.ofNullable(latest),
                              current);
                      }
                      boolean ready;
                      synchronized (gate) {
                        ready = !requests.isEmpty() && batch == null;
                      }
                      if (!ready && monitor.awaitChange(Duration.ofMillis(100))) {
                        synchronized (gate) {
                          requests.addLast(new Request(null, false));
                        }
                      }
                      long capturedEpoch;
                      synchronized (gate) {
                        if (batch != null || requests.isEmpty()) continue;
                        capturedEpoch = epoch;
                        captured = new ArrayList<>(requests);
                        requests.clear();
                      }
                      monitor.update(inputs);
                      var result =
                          reload(capturedEpoch, captured.stream().anyMatch(Request::force));
                      if (result == null) {
                        synchronized (gate) {
                          for (Request request : captured) {
                            if (closed.get()) {
                              if (request.result() != null) request.result().cancel(false);
                            } else requests.addLast(new Request(request.result(), request.force()));
                          }
                        }
                      } else {
                        if (result.outcome() == ExecutionUpdateResult.Outcome.UNCHANGED
                            && captured.stream().allMatch(request -> request.result() == null))
                          continue;
                        latest = result;
                        completed = result;
                        emitResult(result);
                        for (Request request : captured)
                          if (request.result() != null) request.result().complete(result);
                        if (result.outcome() == ExecutionUpdateResult.Outcome.PUBLISHED)
                          progress.accept("Published runtime revision");
                      }
                    } catch (InterruptedException exception) {
                      Thread.currentThread().interrupt();
                      for (Request request : captured)
                        if (request.result() != null) request.result().cancel(false);
                      return;
                    } catch (java.nio.file.ClosedWatchServiceException exception) {
                      for (Request request : captured)
                        if (request.result() != null) request.result().cancel(false);
                      return;
                    } catch (IOException | RuntimeException exception) {
                      if (!closed.get())
                        diagnostics.accept("error[NORM-LIVE-0001]: " + exception.getMessage());
                      if (completed != null || captured.isEmpty()) continue;
                      var result =
                          new ExecutionUpdateResult(
                              ExecutionUpdateResult.Outcome.FAILED,
                              Optional.empty(),
                              execution.status(),
                              List.of(),
                              Optional.ofNullable(exception.getMessage()),
                              RuntimeChangeSet.empty());
                      latest = result;
                      emitResult(result);
                      for (Request request : captured)
                        if (request.result() != null) request.result().complete(result);
                    }
                  }
                });
  }

  private ExecutionUpdateResult reload(long capturedEpoch, boolean force) throws IOException {
    synchronized (gate) {
      if (closed.get() || batch != null || epoch != capturedEpoch) return null;
      if (!force
          && completedInputs.matches()
          && (latest == null || latest.outcome() != ExecutionUpdateResult.Outcome.FAILED)) {
        inputs = completedInputs;
        monitor.update(inputs);
        return latest != null && latest.outcome() == ExecutionUpdateResult.Outcome.REJECTED
            ? new ExecutionUpdateResult(
                latest.outcome(),
                latest.candidate(),
                execution.status(),
                latest.diagnostics(),
                latest.failure(),
                latest.changes())
            : new ExecutionUpdateResult(
                ExecutionUpdateResult.Outcome.UNCHANGED,
                Optional.empty(),
                execution.status(),
                List.of(),
                Optional.empty(),
                RuntimeChangeSet.empty());
      }
    }
    ApplicationCandidate candidate;
    try {
      candidate = compiler.compile();
    } catch (ProjectLoadException exception) {
      inputs = exception.inputs();
      monitor.update(inputs);
      throw exception;
    }
    boolean transferred = false;
    try {
      inputs = candidate.inputs();
      monitor.update(inputs);
      synchronized (gate) {
        if (closed.get() || batch != null || epoch != capturedEpoch || !inputs.matches())
          return null;
        completedInputs = inputs;
        if (!candidate.result().isSuccess()) {
          candidate
              .result()
              .diagnostics()
              .forEach(value -> diagnostics.accept(DiagnosticRenderer.render(value)));
          return new ExecutionUpdateResult(
              ExecutionUpdateResult.Outcome.REJECTED,
              Optional.empty(),
              execution.status(),
              candidate.result().diagnostics(),
              Optional.empty(),
              RuntimeChangeSet.empty());
        }
        var revision = candidate.revision().orElseThrow();
        var identity =
            Optional.of(ArtifactId.forArtifact(revision.artifact(), "norm-source-live-v1"));
        var changes = new RuntimeChangeAnalyzer().compare(publishedArtifact, revision.artifact());
        try {
          execution.update(revision);
        } catch (IllegalArgumentException exception) {
          diagnostics.accept("error[NORM-LIVE-0002]: " + exception.getMessage());
          return new ExecutionUpdateResult(
              ExecutionUpdateResult.Outcome.REJECTED,
              identity,
              execution.status(),
              candidate.result().diagnostics(),
              Optional.ofNullable(exception.getMessage()),
              changes);
        }
        transferred = true;
        publishedArtifact = revision.artifact();
        return new ExecutionUpdateResult(
            ExecutionUpdateResult.Outcome.PUBLISHED,
            identity,
            execution.status(),
            candidate.result().diagnostics(),
            Optional.empty(),
            changes);
      }
    } finally {
      if (!transferred) candidate.close();
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) return;
    List<EventSubscription> activeSubscriptions;
    synchronized (gate) {
      for (Request request : requests) if (request.result() != null) request.result().cancel(false);
      requests.clear();
      batch = null;
      activeSubscriptions = List.copyOf(subscriptions);
      subscriptions.clear();
    }
    activeSubscriptions.forEach(EventSubscription::close);
    try {
      monitor.close();
    } finally {
      if (worker != null) {
        boolean interrupted = Thread.interrupted();
        try {
          worker.interrupt();
          while (worker.isAlive()) {
            try {
              worker.join();
            } catch (InterruptedException exception) {
              interrupted = true;
            }
          }
        } finally {
          if (interrupted) Thread.currentThread().interrupt();
        }
      }
    }
  }
}
