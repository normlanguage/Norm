package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.CoreLiveSchema;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.LiveExecution;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class LiveTruffleExecution implements LiveExecution {
  private CoreLiveSchema schema;
  private final ExecutableProgram executable;
  private final LiveBindings bindings;
  private final AtomicBoolean running = new AtomicBoolean();

  LiveTruffleExecution(CoreArtifact artifact) {
    schema = CoreLiveSchema.create(artifact);
    var lowerer = new Lowerer(null, schema);
    executable = lowerer.lower(artifact, CoreExecutionPlan.forLiveArtifact(artifact));
    bindings = new LiveBindings(lowerer.liveVersion());
  }

  @Override
  public synchronized void update(CoreArtifact artifact) {
    Objects.requireNonNull(artifact, "artifact");
    CoreLiveSchema candidate = CoreLiveSchema.create(artifact);
    schema.requireCompatible(candidate);
    var lowerer = new Lowerer(null, candidate);
    lowerer.lower(artifact, CoreExecutionPlan.forLiveArtifact(artifact));
    bindings.publish(lowerer.liveVersion());
    schema = candidate;
  }

  @Override
  public void execute(ExecutionContext context) {
    Objects.requireNonNull(context, "context");
    if (!running.compareAndSet(false, true))
      throw new IllegalStateException("live execution is already running");
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
