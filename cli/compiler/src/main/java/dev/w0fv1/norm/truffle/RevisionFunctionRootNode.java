package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;

final class RevisionFunctionRootNode extends RootNode {
  private final CallTarget target;
  private final LiveBindings.Version version;
  @Child private IndirectCallNode call = IndirectCallNode.create();

  RevisionFunctionRootNode(CallTarget target, LiveBindings.Version version) {
    super(null);
    this.target = target;
    this.version = version;
  }

  LiveBindings.Version version() {
    return version;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    var execution = (ExecutionState) frame.getArguments()[0];
    var bindings = execution.liveBindings();
    if (bindings == null || version == null) return call.call(target, frame.getArguments());
    var resultVersion = RuntimeInvocation.resultVersion(getCallTarget(), bindings.capture());
    Object result;
    try (var scope = bindings.pin(version)) {
      result = call.call(target, frame.getArguments());
    }
    if (resultVersion == version) return result;
    return RuntimeInvocation.adaptResult(execution, result, resultVersion);
  }
}
