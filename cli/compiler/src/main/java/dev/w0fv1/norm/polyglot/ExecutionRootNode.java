package dev.w0fv1.norm.polyglot;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import dev.w0fv1.norm.application.ApplicationInput;

final class ExecutionRootNode extends RootNode {
  private final ApplicationInput input;
  @Child private DirectCallNode entryPoint;

  ExecutionRootNode(Language language, ApplicationInput input, CallTarget target) {
    super(language);
    this.input = input;
    entryPoint = DirectCallNode.create(target);
  }

  @Override
  public Object execute(VirtualFrame frame) {
    var context = Language.context(this);
    var application = context.application(input);
    try (var runtime = application.openRuntime()) {
      return entryPoint.call(
          application.context(context.execution()).withJarBindingRuntime(runtime));
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("Cannot open application runtime", exception);
    }
  }
}
