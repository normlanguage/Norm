package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.bound.BoundProgram;
import dev.w0fv1.norm.execution.ExecutionBackend;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.GuestStackFrame;
import dev.w0fv1.norm.execution.NormExecutionException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TruffleExecutionBackend implements ExecutionBackend {
  @Override
  public void execute(BoundProgram program, ExecutionContext context) {
    Objects.requireNonNull(program, "program");
    Objects.requireNonNull(context, "context");
    try {
      compile(null, program, context).entryPoint().call();
    } catch (NormGuestException exception) {
      throw translate(exception);
    } finally {
      context.output().flush();
    }
  }

  ExecutableProgram compile(Language language, BoundProgram program, ExecutionContext context) {
    return new Lowerer(language, context).lower(program);
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
