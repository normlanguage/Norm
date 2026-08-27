package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.GuestStackFrame;
import dev.w0fv1.norm.execution.NormExecutionException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TruffleExecutionBackend implements ExecutionBackend {
  private static final int DEFAULT_MAXIMUM_ARTIFACTS = 256;
  private final int maximumArtifacts;
  private final Map<ArtifactId, ExecutableProgram> artifacts;

  public TruffleExecutionBackend() {
    this(DEFAULT_MAXIMUM_ARTIFACTS);
  }

  TruffleExecutionBackend(int maximumArtifacts) {
    if (maximumArtifacts < 1) {
      throw new IllegalArgumentException("maximum artifacts must be positive");
    }
    this.maximumArtifacts = maximumArtifacts;
    artifacts = new LinkedHashMap<>(16, 0.75f, true);
  }

  @Override
  public void execute(CoreArtifact artifact, ExecutionContext context) {
    Objects.requireNonNull(artifact, "artifact");
    Objects.requireNonNull(context, "context");
    try {
      compile(null, artifact).execute(context);
    } catch (NormGuestException exception) {
      throw translate(exception);
    } finally {
      context.output().flush();
    }
  }

  synchronized ExecutableProgram compile(Language language, CoreArtifact artifact) {
    String backendAbi =
        language == null ? "norm-truffle-standalone-v1" : "norm-truffle-language-v1";
    ArtifactId artifactId = ArtifactId.forArtifact(artifact, backendAbi);
    ExecutableProgram executable = artifacts.get(artifactId);
    if (executable != null) return executable;
    executable = new Lowerer(language).lower(artifact);
    artifacts.put(artifactId, executable);
    if (artifacts.size() > maximumArtifacts) {
      artifacts.remove(artifacts.keySet().iterator().next());
    }
    return executable;
  }

  synchronized int cachedArtifacts() {
    return artifacts.size();
  }

  private static NormExecutionException translate(NormGuestException exception) {
    Node location = exception.getLocation();
    SourceSection section = location == null ? null : location.getEncapsulatingSourceSection();
    URI uri = section == null ? URI.create("norm:unknown") : section.getSource().getURI();
    int line = section == null ? 0 : section.getStartLine();
    int column = section == null ? 0 : section.getStartColumn();
    List<GuestStackFrame> stack = new ArrayList<>();
    for (TruffleStackTraceElement element : TruffleStackTrace.getStackTrace(exception)) {
      SourceSection frameSection =
          element.getLocation() == null
              ? element.getTarget().getRootNode().getSourceSection()
              : element.getLocation().getEncapsulatingSourceSection();
      stack.add(
          new GuestStackFrame(
              element.getTarget().getRootNode().getName(),
              frameSection == null ? uri : frameSection.getSource().getURI(),
              frameSection == null ? line : frameSection.getStartLine(),
              frameSection == null ? column : frameSection.getStartColumn()));
    }
    if (stack.isEmpty()) stack = List.of(new GuestStackFrame("<guest>", uri, line, column));
    return new NormExecutionException(
        exception.code(), exception.getMessage(), uri, line, column, stack, exception);
  }
}
