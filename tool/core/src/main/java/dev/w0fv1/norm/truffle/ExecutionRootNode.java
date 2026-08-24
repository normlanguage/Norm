package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

final class ExecutionRootNode extends RootNode {
  @Child private DirectCallNode entryPoint;

  ExecutionRootNode(Language language, CallTarget entryPoint) {
    super(language);
    this.entryPoint = DirectCallNode.create(entryPoint);
  }

  @Override
  public Object execute(VirtualFrame frame) {
    return entryPoint.call(Language.context(this).execution());
  }
}
