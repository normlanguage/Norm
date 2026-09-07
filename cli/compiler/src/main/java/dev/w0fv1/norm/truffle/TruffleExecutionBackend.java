package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.abi.BuiltinAbi;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreExecutionPlan;
import dev.w0fv1.norm.core.DebugInfoId;
import dev.w0fv1.norm.core.ExecutableId;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.GuestStackFrame;
import dev.w0fv1.norm.execution.NormExecutionException;
import dev.w0fv1.norm.execution.PreparedExecution;
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
  public void execute(
      CoreArtifact artifact, CoreExecutionPlan execution, ExecutionContext context) {
    prepare(artifact, execution).execute(context);
  }

  public PreparedExecution prepare(CoreArtifact artifact) {
    return prepare(artifact, CoreExecutionPlan.forArtifact(artifact));
  }

  public PreparedExecution prepare(CoreArtifact artifact, CoreExecutionPlan execution) {
    Objects.requireNonNull(artifact, "artifact");
    return new PreparedTruffleProgram(
        RuntimeSourceMap.from(artifact.authoring()), compile(null, artifact, execution));
  }

  public com.oracle.truffle.api.RootCallTarget instrumentedEntryPoint(
      com.oracle.truffle.api.TruffleLanguage<?> language,
      CoreArtifact artifact,
      CoreExecutionPlan execution) {
    ExecutableProgram executable = compile(language, artifact, execution);
    return new com.oracle.truffle.api.nodes.RootNode(language) {
      @Override
      public Object execute(com.oracle.truffle.api.frame.VirtualFrame frame) {
        ExecutionContext context = (ExecutionContext) frame.getArguments()[0];
        try {
          return executable.execute(context);
        } finally {
          context.output().flush();
        }
      }
    }.getCallTarget();
  }

  synchronized ExecutableProgram compile(
      com.oracle.truffle.api.TruffleLanguage<?> language, CoreArtifact artifact) {
    return compile(language, artifact, CoreExecutionPlan.forArtifact(artifact));
  }

  synchronized ExecutableProgram compile(
      com.oracle.truffle.api.TruffleLanguage<?> language,
      CoreArtifact artifact,
      CoreExecutionPlan execution) {
    String backendAbi =
        (language == null ? "norm-truffle-standalone-v1:" : "norm-truffle-language-v1:")
            + BuiltinAbi.FINGERPRINT;
    CacheKey cacheKey =
        new CacheKey(
            ExecutableId.forArtifact(artifact, backendAbi),
            language == null ? null : DebugInfoId.forArtifact(artifact),
            execution);
    ExecutableProgram executable = artifacts.get(cacheKey);
    if (executable != null) return executable;
    executable = new Lowerer(language).lower(artifact, execution);
    artifacts.put(cacheKey, executable);
    if (artifacts.size() > maximumArtifacts) {
      artifacts.remove(artifacts.keySet().iterator().next());
    }
    return executable;
  }

  synchronized int cachedArtifacts() {
    return artifacts.size();
  }

  static NormExecutionException translate(
      NormGuestException exception, RuntimeSourceMap locations) {
    Node location = exception.getLocation();
    GuestLocation failure = location(location, locations);
    URI uri = failure.uri();
    int line = failure.line();
    int column = failure.column();
    List<GuestStackFrame> stack = new ArrayList<>();
    for (TruffleStackTraceElement element : TruffleStackTrace.getStackTrace(exception)) {
      Node frameNode =
          element.getLocation() == null ? element.getTarget().getRootNode() : element.getLocation();
      GuestLocation frame = location(frameNode, locations);
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

  private static GuestLocation location(Node node, RuntimeSourceMap locations) {
    if (node instanceof RuntimeLocation runtimeLocation) {
      var position = locations.location(runtimeLocation.occurrence(), runtimeLocation.nodeIndex());
      return new GuestLocation(position.uri(), position.line(), position.column(), true);
    }
    SourceSection section = node == null ? null : node.getEncapsulatingSourceSection();
    return section == null
        ? GuestLocation.unknown()
        : new GuestLocation(
            section.getSource().getURI(), section.getStartLine(), section.getStartColumn(), true);
  }

  private record CacheKey(
      ExecutableId executable, DebugInfoId debug, CoreExecutionPlan execution) {}

  private record GuestLocation(URI uri, int line, int column, boolean known) {
    private static GuestLocation unknown() {
      return new GuestLocation(URI.create("norm:unknown"), 0, 0, false);
    }
  }
}
