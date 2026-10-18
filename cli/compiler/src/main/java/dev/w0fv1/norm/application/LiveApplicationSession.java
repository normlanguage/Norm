package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.ExecutionUpdateEvent;
import dev.w0fv1.norm.execution.ExecutionUpdateEventKind;
import dev.w0fv1.norm.execution.ExecutionUpdatePreview;
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
import java.util.List;
import java.util.Objects;
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

  private record Request(
      CompletableFuture<ExecutionUpdateResult> result,
      CompletableFuture<ExecutionUpdatePreview> preview,
      boolean force,
      Long batchIdentity) {
    void cancel() {
      if (result != null) result.cancel(false);
      if (preview != null) preview.cancel(false);
    }
  }

  private record ReviewedUpdate(ApplicationCandidate candidate, ExecutionUpdatePreview preview)
      implements AutoCloseable {
    @Override
    public void close() {
      candidate.close();
    }
  }

  private static final class BatchState {
    private final long identity;
    private boolean previewing;
    private boolean reviewed;
    private boolean committing;
    private ReviewedUpdate prepared;

    private BatchState(long identity) {
      this.identity = identity;
    }
  }

  private record ReloadOutcome(ExecutionUpdateResult result, ExecutionUpdatePreview preview) {}

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
  private BatchState batch;
  private boolean automaticPending;
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

  ExecutionUpdateSubscription onUpdate(
      long afterSequence, Consumer<ExecutionUpdateEvent> listener) {
    if (afterSequence < 0) throw new IllegalArgumentException("afterSequence must not be negative");
    Objects.requireNonNull(listener, "listener");
    synchronized (gate) {
      if (closed.get()) throw new IllegalStateException("update coordinator is closed");
      var subscription = new EventSubscription(listener);
      eventHistory.stream()
          .filter(event -> event.sequence() > afterSequence)
          .forEach(subscription.events::offer);
      subscriptions.add(subscription);
      subscription.start();
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
    private final Consumer<ExecutionUpdateEvent> listener;
    private Thread callbackThread;

    private EventSubscription() {
      this(null);
    }

    private EventSubscription(Consumer<ExecutionUpdateEvent> listener) {
      this.listener = listener;
    }

    private void start() {
      if (listener != null)
        callbackThread =
            Thread.ofVirtual().name("norm-runtime-update-callback").start(this::dispatch);
    }

    private void dispatch() {
      try {
        while (!closed.get()) listener.accept(events.take());
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      } catch (RuntimeException failure) {
        close();
      }
    }

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
      if (callbackThread != null && callbackThread != Thread.currentThread())
        callbackThread.interrupt();
    }
  }

  dev.w0fv1.norm.execution.ExecutionUpdates.Batch beginBatch() {
    long identity;
    synchronized (gate) {
      if (closed.get()) throw new IllegalStateException("update coordinator is closed");
      if (batch != null) throw new IllegalStateException("A source update batch is already open");
      identity = ++epoch;
      batch = new BatchState(identity);
    }
    return new dev.w0fv1.norm.execution.ExecutionUpdates.Batch() {
      @Override
      public CompletableFuture<ExecutionUpdatePreview> preview() {
        return enqueuePreview(identity);
      }

      @Override
      public CompletableFuture<ExecutionUpdateResult> commit() {
        synchronized (gate) {
          if (batch == null || batch.identity != identity || batch.committing || batch.previewing)
            throw new IllegalStateException("batch is closed or committing");
          if (!batch.reviewed) {
            batch = null;
            return enqueueUpdate(false);
          }
          batch.committing = true;
          var result = new CompletableFuture<ExecutionUpdateResult>();
          requests.addLast(new Request(result, null, false, identity));
          emit(ExecutionUpdateEventKind.REQUESTED, Optional.empty(), execution.status());
          return result;
        }
      }

      @Override
      public void close() {
        synchronized (gate) {
          if (batch == null || batch.identity != identity) return;
          if (batch.prepared != null) batch.prepared.close();
          batch = null;
          epoch++;
          automaticPending = true;
        }
      }
    };
  }

  CompletableFuture<ExecutionUpdateResult> requestUpdate() {
    return enqueueUpdate(true);
  }

  CompletableFuture<ExecutionUpdatePreview> previewUpdate() {
    return enqueuePreview(null);
  }

  private CompletableFuture<ExecutionUpdatePreview> enqueuePreview(Long identity) {
    synchronized (gate) {
      if (closed.get()) throw new IllegalStateException("update coordinator is closed");
      if (identity == null
          ? batch != null
          : batch == null || batch.identity != identity || batch.committing || batch.previewing)
        throw new IllegalStateException(
            "Use the active source update batch to preview its changes");
      var result = new CompletableFuture<ExecutionUpdatePreview>();
      if (identity == null) epoch++;
      else batch.previewing = true;
      requests.addLast(new Request(null, result, true, identity));
      return result;
    }
  }

  private CompletableFuture<ExecutionUpdateResult> enqueueUpdate(boolean force) {
    synchronized (gate) {
      if (closed.get()) throw new IllegalStateException("update coordinator is closed");
      if (batch != null)
        throw new IllegalStateException(
            "Commit or close the open source update batch before requesting publication");
      epoch++;
      var result = new CompletableFuture<ExecutionUpdateResult>();
      requests.addLast(new Request(result, null, force, null));
      emit(ExecutionUpdateEventKind.REQUESTED, Optional.empty(), execution.status());
      return result;
    }
  }

  void start() {
    synchronized (gate) {
      automaticPending = !inputs.matches();
    }
    worker =
        Thread.ofVirtual()
            .name("norm-live-compilation")
            .start(
                () -> {
                  while (!closed.get()) {
                    Request captured = null;
                    boolean completed = false;
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
                        else if (wasPending)
                          progress.accept("Completed pending runtime transitions");
                        status = current;
                        if (wasPending && current.pendingLocations().isEmpty())
                          emit(
                              ExecutionUpdateEventKind.ADOPTED,
                              Optional.ofNullable(latest),
                              current);
                      }
                      long capturedEpoch;
                      synchronized (gate) {
                        var iterator = requests.iterator();
                        while (iterator.hasNext()) {
                          var request = iterator.next();
                          if (request.batchIdentity() != null
                              && (batch == null || batch.identity != request.batchIdentity())) {
                            iterator.remove();
                            request.cancel();
                          } else if (batch == null || request.batchIdentity() != null) {
                            iterator.remove();
                            captured = request;
                            break;
                          }
                        }
                        if (captured == null && batch == null && automaticPending) {
                          automaticPending = false;
                          captured = new Request(null, null, false, null);
                        }
                        capturedEpoch = epoch;
                      }
                      if (captured == null) {
                        if (monitor.awaitChange(Duration.ofMillis(100))) {
                          synchronized (gate) {
                            automaticPending = true;
                          }
                        }
                        continue;
                      }
                      var outcome = reload(capturedEpoch, captured);
                      if (outcome == null) {
                        synchronized (gate) {
                          if (closed.get()) captured.cancel();
                          else requests.addFirst(captured);
                        }
                        continue;
                      }
                      completed = true;
                      complete(captured, outcome);
                      if (outcome.result() != null
                          && outcome.result().outcome() == ExecutionUpdateResult.Outcome.PUBLISHED)
                        progress.accept("Published runtime revision");
                    } catch (InterruptedException exception) {
                      Thread.currentThread().interrupt();
                      if (captured != null) captured.cancel();
                      return;
                    } catch (java.nio.file.ClosedWatchServiceException exception) {
                      if (captured != null) captured.cancel();
                      return;
                    } catch (IOException | RuntimeException exception) {
                      if (!completed && captured != null) {
                        var failure = Optional.ofNullable(exception.getMessage());
                        complete(
                            captured,
                            captured.preview() != null
                                ? new ReloadOutcome(
                                    null,
                                    new ExecutionUpdatePreview(
                                        ExecutionUpdatePreview.Outcome.FAILED,
                                        Optional.empty(),
                                        execution.status(),
                                        List.of(),
                                        failure,
                                        RuntimeChangeSet.empty()))
                                : new ReloadOutcome(
                                    new ExecutionUpdateResult(
                                        ExecutionUpdateResult.Outcome.FAILED,
                                        Optional.empty(),
                                        execution.status(),
                                        List.of(),
                                        failure,
                                        RuntimeChangeSet.empty()),
                                    null));
                      }
                      if (!closed.get())
                        diagnostics.accept("error[NORM-LIVE-0001]: " + exception.getMessage());
                    }
                  }
                });
  }

  private void complete(Request request, ReloadOutcome outcome) {
    synchronized (gate) {
      if (request.batchIdentity() != null
          && batch != null
          && batch.identity == request.batchIdentity()) {
        batch.committing = false;
        batch.previewing = false;
        if (outcome.preview() != null)
          batch.reviewed =
              outcome.preview().outcome() == ExecutionUpdatePreview.Outcome.PREVIEWED
                  || outcome.preview().outcome() == ExecutionUpdatePreview.Outcome.UNCHANGED;
      }
      if (outcome.preview() != null) request.preview().complete(outcome.preview());
      if (outcome.result() != null) {
        var result = outcome.result();
        if (request.result() == null && result.outcome() == ExecutionUpdateResult.Outcome.UNCHANGED)
          return;
        latest = result;
        emitResult(result);
        if (request.result() != null) request.result().complete(result);
      }
    }
  }

  private ReloadOutcome reload(long capturedEpoch, Request request) throws IOException {
    synchronized (gate) {
      if (closed.get() || epoch != capturedEpoch) return null;
      if (request.batchIdentity() != null) {
        if (batch == null || batch.identity != request.batchIdentity()) return null;
        if (request.preview() == null) {
          var prepared = batch.prepared;
          batch.prepared = null;
          batch.reviewed = false;
          if (prepared == null || !prepared.candidate().inputs().matches()) {
            if (prepared != null) prepared.close();
            return new ReloadOutcome(
                new ExecutionUpdateResult(
                    ExecutionUpdateResult.Outcome.REJECTED,
                    Optional.empty(),
                    execution.status(),
                    List.of(),
                    Optional.of("Preview is stale; preview the current sources before committing"),
                    RuntimeChangeSet.empty()),
                null);
          }
          var result = publish(prepared);
          if (result.outcome() == ExecutionUpdateResult.Outcome.PUBLISHED
              || result.outcome() == ExecutionUpdateResult.Outcome.UNCHANGED) {
            batch = null;
            epoch++;
          }
          return new ReloadOutcome(result, null);
        }
        batch.reviewed = true;
        if (batch.prepared != null) batch.prepared.close();
        batch.prepared = null;
      } else if (batch != null) return null;
      if (request.preview() == null
          && !request.force()
          && completedInputs.matches()
          && (latest == null || latest.outcome() != ExecutionUpdateResult.Outcome.FAILED)) {
        inputs = completedInputs;
        monitor.update(inputs);
        return new ReloadOutcome(
            latest != null && latest.outcome() == ExecutionUpdateResult.Outcome.REJECTED
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
                    RuntimeChangeSet.empty()),
            null);
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
    boolean retained = false;
    try {
      inputs = candidate.inputs();
      monitor.update(inputs);
      synchronized (gate) {
        if (closed.get() || epoch != capturedEpoch || !inputs.matches()) return null;
        var revision = candidate.revision();
        var identity =
            revision.map(value -> ArtifactId.forArtifact(value.artifact(), "norm-source-live-v1"));
        var changes =
            revision
                .map(
                    value ->
                        new RuntimeChangeAnalyzer().compare(publishedArtifact, value.artifact()))
                .orElseGet(RuntimeChangeSet::empty);
        var preview =
            new ExecutionUpdatePreview(
                !candidate.result().isSuccess()
                    ? ExecutionUpdatePreview.Outcome.REJECTED
                    : identity
                            .orElseThrow()
                            .equals(
                                ArtifactId.forArtifact(publishedArtifact, "norm-source-live-v1"))
                        ? ExecutionUpdatePreview.Outcome.UNCHANGED
                        : ExecutionUpdatePreview.Outcome.PREVIEWED,
                identity,
                execution.status(),
                candidate.result().diagnostics(),
                Optional.empty(),
                changes);
        var prepared = new ReviewedUpdate(candidate, preview);
        if (request.preview() != null) {
          if (request.batchIdentity() != null) {
            batch.prepared = prepared;
            retained = true;
          }
          return new ReloadOutcome(null, preview);
        }
        retained = true;
        return new ReloadOutcome(publish(prepared), null);
      }
    } finally {
      if (!retained) candidate.close();
    }
  }

  private ExecutionUpdateResult publish(ReviewedUpdate prepared) {
    boolean transferred = false;
    var candidate = prepared.candidate();
    var preview = prepared.preview();
    try {
      completedInputs = candidate.inputs();
      if (!candidate.result().isSuccess()) {
        candidate
            .result()
            .diagnostics()
            .forEach(value -> diagnostics.accept(DiagnosticRenderer.render(value)));
        return new ExecutionUpdateResult(
            ExecutionUpdateResult.Outcome.REJECTED,
            preview.candidate(),
            execution.status(),
            preview.diagnostics(),
            preview.failure(),
            preview.changes());
      }
      if (preview.outcome() == ExecutionUpdatePreview.Outcome.UNCHANGED)
        return new ExecutionUpdateResult(
            ExecutionUpdateResult.Outcome.UNCHANGED,
            preview.candidate(),
            execution.status(),
            preview.diagnostics(),
            Optional.empty(),
            preview.changes());
      var revision = candidate.revision().orElseThrow();
      try {
        execution.update(revision);
      } catch (IllegalArgumentException exception) {
        diagnostics.accept("error[NORM-LIVE-0002]: " + exception.getMessage());
        return new ExecutionUpdateResult(
            ExecutionUpdateResult.Outcome.REJECTED,
            preview.candidate(),
            execution.status(),
            preview.diagnostics(),
            Optional.ofNullable(exception.getMessage()),
            preview.changes());
      }
      transferred = true;
      publishedArtifact = revision.artifact();
      return new ExecutionUpdateResult(
          ExecutionUpdateResult.Outcome.PUBLISHED,
          preview.candidate(),
          execution.status(),
          preview.diagnostics(),
          Optional.empty(),
          preview.changes());
    } finally {
      if (!transferred) prepared.close();
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) return;
    List<EventSubscription> activeSubscriptions;
    synchronized (gate) {
      for (Request request : requests) request.cancel();
      requests.clear();
      if (batch != null && batch.prepared != null) batch.prepared.close();
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
