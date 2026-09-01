package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.abi.BuiltinAbi;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.DebugInfoId;
import dev.w0fv1.norm.core.ExecutableId;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.GuestStackFrame;
import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.value.SourceSpan;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TruffleExecutionBackend implements ExecutionBackend {
  private static final int DEFAULT_MAXIMUM_ARTIFACTS = 256;
  private final int maximumArtifacts;
  private final Map<CacheKey, ExecutableProgram> artifacts;

  public TruffleExecutionBackend() {
    this(DEFAULT_MAXIMUM_ARTIFACTS);
  }

  public TruffleExecutionBackend(int maximumArtifacts) {
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
      throw translate(exception, artifact);
    } finally {
      context.output().flush();
    }
  }

  synchronized ExecutableProgram compile(Language language, CoreArtifact artifact) {
    String backendAbi =
        (language == null ? "norm-truffle-standalone-v1:" : "norm-truffle-language-v1:")
            + BuiltinAbi.FINGERPRINT;
    CacheKey cacheKey =
        new CacheKey(
            ExecutableId.forArtifact(artifact, backendAbi),
            language == null ? null : DebugInfoId.forArtifact(artifact));
    ExecutableProgram executable = artifacts.get(cacheKey);
    if (executable != null) return executable;
    executable = new Lowerer(language).lower(artifact);
    artifacts.put(cacheKey, executable);
    if (artifacts.size() > maximumArtifacts) {
      artifacts.remove(artifacts.keySet().iterator().next());
    }
    return executable;
  }

  synchronized int cachedArtifacts() {
    return artifacts.size();
  }

  private static NormExecutionException translate(
      NormGuestException exception, CoreArtifact artifact) {
    Node location = exception.getLocation();
    GuestLocation failure = location(location, artifact);
    URI uri = failure.uri();
    int line = failure.line();
    int column = failure.column();
    List<GuestStackFrame> stack = new ArrayList<>();
    for (TruffleStackTraceElement element : TruffleStackTrace.getStackTrace(exception)) {
      Node frameNode =
          element.getLocation() == null ? element.getTarget().getRootNode() : element.getLocation();
      GuestLocation frame = location(frameNode, artifact);
      stack.add(
          new GuestStackFrame(
              element.getTarget().getRootNode().getName(),
              frame.known() ? frame.uri() : uri,
              frame.known() ? frame.line() : line,
              frame.known() ? frame.column() : column));
    }
    if (stack.isEmpty()) stack = List.of(new GuestStackFrame("<guest>", uri, line, column));
    Throwable cause =
        exception instanceof NormThrownException thrown
                && thrown.value.hostValue instanceof Throwable host
            ? host
            : exception;
    return new NormExecutionException(
        exception.code(), exception.getMessage(), uri, line, column, stack, cause);
  }

  private static GuestLocation location(Node node, CoreArtifact artifact) {
    if (node instanceof RuntimeLocation runtimeLocation) {
      SourceSpan span =
          artifact
              .authoring()
              .span(runtimeLocation.occurrence(), runtimeLocation.nodeIndex())
              .orElseGet(
                  () -> artifact.authoring().origin(runtimeLocation.occurrence()).rootSpan());
      return new GuestLocation(
          span.source().id().uri(), span.start().line(), span.start().column(), true);
    }
    SourceSection section = node == null ? null : node.getEncapsulatingSourceSection();
    return section == null
        ? GuestLocation.unknown()
        : new GuestLocation(
            section.getSource().getURI(), section.getStartLine(), section.getStartColumn(), true);
  }

  private record CacheKey(ExecutableId executable, DebugInfoId debug) {}

  private record GuestLocation(URI uri, int line, int column, boolean known) {
    private static GuestLocation unknown() {
      return new GuestLocation(URI.create("norm:unknown"), 0, 0, false);
    }
  }
}
