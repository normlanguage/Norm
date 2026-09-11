package dev.w0fv1.norm.frontend;

import java.util.Objects;

final class AnalysisTransaction {
  private final SemanticModelBuilder model;
  private final BodyAnalysisState body;
  private final TypeResolutionState resolution;
  private final DiagnosticBag diagnostics;
  private Probe active;

  AnalysisTransaction(
      SemanticModelBuilder model,
      BodyAnalysisState body,
      TypeResolutionState resolution,
      DiagnosticBag diagnostics) {
    this.model = Objects.requireNonNull(model, "model");
    this.body = Objects.requireNonNull(body, "body");
    this.resolution = Objects.requireNonNull(resolution, "resolution");
    this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
  }

  Probe probe() {
    var probe = new Probe(active);
    active = probe;
    return probe;
  }

  final class Probe implements AutoCloseable {
    private final Probe parent;
    private final SemanticModelBuilder.Checkpoint semantic = model.checkpoint();
    private final BodyAnalysisState.Checkpoint flow = body.checkpoint();
    private final TypeResolutionState.Checkpoint types = resolution.checkpoint();
    private final int diagnosticMark = diagnostics.mark();
    private boolean closed;

    private Probe(Probe parent) {
      this.parent = parent;
    }

    boolean hasErrors() {
      if (closed) throw new IllegalStateException("Analysis probe is closed");
      return diagnostics.hasErrorsSince(diagnosticMark);
    }

    @Override
    public void close() {
      if (closed) return;
      if (active != this)
        throw new IllegalStateException("Analysis probes must close in nesting order");
      model.restore(semantic);
      body.restore(flow);
      resolution.restore(types);
      diagnostics.rollback(diagnosticMark);
      active = parent;
      closed = true;
    }
  }
}
