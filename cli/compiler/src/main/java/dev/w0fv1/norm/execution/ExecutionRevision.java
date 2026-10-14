package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.CoreArtifact;
import java.lang.ref.Reference;
import java.util.Objects;

public record ExecutionRevision(
    CoreArtifact artifact, ExecutionContext context, RevisionResources.Lease resources)
    implements AutoCloseable {
  public ExecutionRevision {
    Objects.requireNonNull(artifact);
    Objects.requireNonNull(context);
    Objects.requireNonNull(resources);
  }

  public static ExecutionRevision unmanaged(CoreArtifact artifact, ExecutionContext context) {
    return new ExecutionRevision(artifact, context, RevisionResources.own(() -> {}));
  }

  public ExecutionRevision retain() {
    return new ExecutionRevision(artifact, context, resources.retain());
  }

  public void retain(Object value) {
    resources.retain(value);
  }

  @Override
  public void close() {
    try {
      resources.close();
    } finally {
      Reference.reachabilityFence(context);
    }
  }
}
