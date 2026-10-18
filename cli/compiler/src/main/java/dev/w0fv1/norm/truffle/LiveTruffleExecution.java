package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.CoreLiveSchema;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.ExecutionRevision;
import dev.w0fv1.norm.execution.LiveExecution;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class LiveTruffleExecution implements LiveExecution {
  private final ExecutableProgram executable;
  private final LiveBindings bindings;
  private volatile boolean closed;
  private final AtomicBoolean running = new AtomicBoolean();

  LiveTruffleExecution(CoreArtifact artifact) {
    this(artifact, null);
  }

  LiveTruffleExecution(ExecutionRevision revision) {
    this(revision.artifact(), revision);
  }

  private LiveTruffleExecution(CoreArtifact artifact, ExecutionRevision revision) {
    CoreLiveSchema schema = CoreLiveSchema.create(artifact);
    var lowerer = new Lowerer(null, schema);
    executable = lowerer.lower(artifact, CoreExecutionPlan.forLiveArtifact(artifact));
    var version = lowerer.liveVersion();
    version.attachRuntime(executable);
    if (revision != null) version.attach(revision);
    bindings = new LiveBindings(version);
  }

  @Override
  public synchronized void update(CoreArtifact artifact) {
    ExecutionRevision current = bindings.latestRevision();
    ExecutionRevision candidate =
        current == null
            ? null
            : new ExecutionRevision(artifact, current.context(), current.resources().retain());
    try {
      updateCandidate(artifact, candidate);
    } catch (RuntimeException | Error failure) {
      if (candidate != null) candidate.close();
      throw failure;
    }
  }

  @Override
  public synchronized void update(ExecutionRevision revision) {
    updateCandidate(revision.artifact(), revision);
  }

  private void updateCandidate(CoreArtifact artifact, ExecutionRevision revision) {
    if (closed) throw new IllegalStateException("Live execution is closed");
    Objects.requireNonNull(artifact, "artifact");
    CoreLiveSchema candidate = CoreLiveSchema.create(artifact);
    bindings.requireCompatible(candidate);
    var lowerer = new Lowerer(null, candidate);
    var prepared = lowerer.lower(artifact, CoreExecutionPlan.forLiveArtifact(artifact));
    var version = lowerer.liveVersion();
    version.attachRuntime(prepared);
    if (revision != null) version.attach(revision);
    bindings.publish(version);
  }

  @Override
  public dev.w0fv1.norm.execution.LiveExecutionStatus status() {
    return bindings.status();
  }

  @Override
  public synchronized void close() {
    if (running.get()) throw new IllegalStateException("Cannot close active execution");
    if (closed) return;
    closed = true;
    bindings.close();
  }

  @Override
  public void execute(ExecutionContext context) {
    Objects.requireNonNull(context, "context");
    synchronized (this) {
      if (closed) throw new IllegalStateException("Live execution is closed");
      if (!running.compareAndSet(false, true))
        throw new IllegalStateException("live execution is already running");
    }
    try {
      executable.execute(context, bindings);
    } catch (NormGuestException exception) {
      throw TruffleExecutionBackend.translate(exception, null);
    } finally {
      context.output().flush();
      running.set(false);
    }
  }
}
