package dev.w0fv1.norm.application;

import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.LiveExecution;
import dev.w0fv1.norm.execution.LiveExecutionStatus;
import dev.w0fv1.norm.project.ProjectInputSnapshot;
import dev.w0fv1.norm.project.ProjectLoadException;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class LiveApplicationSession implements AutoCloseable {
  @FunctionalInterface
  interface CandidateCompiler {
    ApplicationCandidate compile() throws IOException;
  }

  private record Request(CompletableFuture<ApplicationUpdateResult> result, boolean force) {}

  private final Object gate = new Object();
  private final CandidateCompiler compiler;
  private final LiveExecution execution;
  private final Consumer<String> diagnostics;
  private final Consumer<String> progress;
  private final InputChangeMonitor monitor;
  private final AtomicBoolean closed = new AtomicBoolean();
  private final ArrayDeque<Request> requests = new ArrayDeque<>();
  private final Set<Long> batches = new HashSet<>();
  private long epoch;
  private ProjectInputSnapshot inputs;
  private Thread worker;
  private LiveExecutionStatus status;

  LiveApplicationSession(
      ProjectInputSnapshot inputs,
      LiveExecution execution,
      CandidateCompiler compiler,
      Consumer<String> progress,
      Consumer<String> diagnostics)
      throws IOException {
    this.compiler = compiler;
    this.inputs = inputs;
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

  long beginBatch() {
    synchronized (gate) {
      if (closed.get()) throw new IllegalStateException("update coordinator is closed");
      long identity = ++epoch;
      batches.add(identity);
      return identity;
    }
  }

  CompletableFuture<ApplicationUpdateResult> commitBatch(long identity) {
    synchronized (gate) {
      if (!batches.remove(identity)) throw new IllegalStateException("batch is closed");
      return requestUpdate();
    }
  }

  void closeBatch(long identity) {
    synchronized (gate) {
      if (!batches.remove(identity)) return;
      epoch++;
      requests.addLast(new Request(null, false));
    }
  }

  CompletableFuture<ApplicationUpdateResult> requestUpdate() {
    synchronized (gate) {
      if (closed.get()) throw new IllegalStateException("update coordinator is closed");
      epoch++;
      var result = new CompletableFuture<ApplicationUpdateResult>();
      requests.addLast(new Request(result, true));
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
                    try {
                      var current = execution.status();
                      if (!status.equals(current)) {
                        if (!current.pendingLocations().isEmpty())
                          diagnostics.accept(
                              "info[NORM-LIVE-0003]: Runtime revision "
                                  + current.revision()
                                  + " is pending for active locations: "
                                  + String.join(", ", current.pendingLocations()));
                        else if (!status.pendingLocations().isEmpty())
                          progress.accept("Completed pending runtime transitions");
                        status = current;
                      }
                      boolean ready;
                      synchronized (gate) {
                        ready = !requests.isEmpty() && batches.isEmpty();
                      }
                      if (!ready && monitor.awaitChange(Duration.ofMillis(100))) {
                        synchronized (gate) {
                          requests.addLast(new Request(null, false));
                        }
                      }
                      long capturedEpoch;
                      synchronized (gate) {
                        if (!batches.isEmpty() || requests.isEmpty()) continue;
                        capturedEpoch = epoch;
                        captured = new ArrayList<>(requests);
                        requests.clear();
                      }
                      monitor.update(inputs);
                      if (captured.stream().noneMatch(Request::force) && inputs.matches()) continue;
                      var result = reload(capturedEpoch);
                      if (result == null) {
                        synchronized (gate) {
                          for (Request request : captured) {
                            if (closed.get()) {
                              if (request.result() != null) request.result().cancel(false);
                            } else requests.addLast(new Request(request.result(), true));
                          }
                        }
                      } else {
                        for (Request request : captured)
                          if (request.result() != null) request.result().complete(result);
                        if (result.outcome() == ApplicationUpdateResult.Outcome.PUBLISHED)
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
                      var result =
                          new ApplicationUpdateResult(
                              ApplicationUpdateResult.Outcome.FAILED,
                              Optional.empty(),
                              execution.status(),
                              List.of(),
                              Optional.ofNullable(exception.getMessage()));
                      for (Request request : captured)
                        if (request.result() != null) request.result().complete(result);
                    }
                  }
                });
  }

  private ApplicationUpdateResult reload(long capturedEpoch) throws IOException {
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
        if (closed.get() || !batches.isEmpty() || epoch != capturedEpoch || !inputs.matches())
          return null;
        if (!candidate.result().isSuccess()) {
          candidate
              .result()
              .diagnostics()
              .forEach(value -> diagnostics.accept(DiagnosticRenderer.render(value)));
          return new ApplicationUpdateResult(
              ApplicationUpdateResult.Outcome.REJECTED,
              Optional.empty(),
              execution.status(),
              candidate.result().diagnostics(),
              Optional.empty());
        }
        var revision = candidate.revision().orElseThrow();
        var identity =
            Optional.of(ArtifactId.forArtifact(revision.artifact(), "norm-source-live-v1"));
        try {
          execution.update(revision);
        } catch (IllegalArgumentException exception) {
          diagnostics.accept("error[NORM-LIVE-0002]: " + exception.getMessage());
          return new ApplicationUpdateResult(
              ApplicationUpdateResult.Outcome.REJECTED,
              identity,
              execution.status(),
              candidate.result().diagnostics(),
              Optional.ofNullable(exception.getMessage()));
        }
        transferred = true;
        return new ApplicationUpdateResult(
            ApplicationUpdateResult.Outcome.PUBLISHED,
            identity,
            execution.status(),
            candidate.result().diagnostics(),
            Optional.empty());
      }
    } finally {
      if (!transferred) candidate.close();
    }
  }

  @Override
  public void close() throws IOException {
    if (!closed.compareAndSet(false, true)) return;
    synchronized (gate) {
      for (Request request : requests) if (request.result() != null) request.result().cancel(false);
      requests.clear();
      batches.clear();
    }
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
