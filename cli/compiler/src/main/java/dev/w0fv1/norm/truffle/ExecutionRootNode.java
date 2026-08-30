package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

final class ExecutionRootNode extends RootNode {
  private final ExecutableProgram executable;
  @Child private DirectCallNode entryPoint;

  ExecutionRootNode(Language language, ExecutableProgram executable) {
    super(language);
    this.executable = executable;
    this.entryPoint = DirectCallNode.create(executable.entryPoint());
  }

  @Override
  public Object execute(VirtualFrame frame) {
    ExecutionState state = executable.execution(Language.context(this).execution());
    Throwable failure = null;
    try {
      return entryPoint.call(state);
    } catch (RuntimeException | Error exception) {
      failure = exception;
      throw exception;
    } finally {
      state.close(failure);
    }
  }
}
