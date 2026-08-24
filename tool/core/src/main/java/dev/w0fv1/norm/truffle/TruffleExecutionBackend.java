package dev.w0fv1.norm.truffle;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.core.ArtifactId;
import dev.w0fv1.norm.core.CoreCompilation;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.GuestStackFrame;
import dev.w0fv1.norm.execution.NormExecutionException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TruffleExecutionBackend implements ExecutionBackend {
  private static final int DEFAULT_MAXIMUM_ARTIFACTS = 256;
  private final Cache<ArtifactId, ExecutableProgram> artifacts;

  public TruffleExecutionBackend() {
    this(DEFAULT_MAXIMUM_ARTIFACTS);
  }

  TruffleExecutionBackend(int maximumArtifacts) {
    if (maximumArtifacts < 1) {
      throw new IllegalArgumentException("maximum artifacts must be positive");
    }
    artifacts = Caffeine.newBuilder().maximumSize(maximumArtifacts).build();
  }

  @Override
  public void execute(CoreCompilation compilation, ExecutionContext context) {
    Objects.requireNonNull(compilation, "compilation");
    Objects.requireNonNull(context, "context");
    try {
      compile(null, compilation).entryPoint().call(context);
    } catch (NormGuestException exception) {
      throw translate(exception);
    } finally {
      context.output().flush();
    }
  }

  ExecutableProgram compile(Language language, CoreCompilation compilation) {
    String backendAbi =
        language == null ? "norm-truffle-standalone-v1" : "norm-truffle-language-v1";
    ArtifactId artifact = ArtifactId.forCompilation(compilation, backendAbi);
    return artifacts.get(artifact, ignored -> new Lowerer(language).lower(compilation));
  }

  int cachedArtifacts() {
    artifacts.cleanUp();
    return Math.toIntExact(artifacts.estimatedSize());
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
