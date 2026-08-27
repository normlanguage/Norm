package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.core.CoreType;
import java.util.List;

final class AnnotationLifecycleNode extends Node {
  @Child private IndirectCallNode call = IndirectCallNode.create();

  Object execute(
      AnnotationRuntime.LifecycleDispatch target,
      ExecutionState execution,
      RuntimeValues.ObjectValue annotation,
      Object[] parameters,
      CoreType[] methodTypeArguments) {
    List<CoreType> receiverTypeArguments = target.receiverTypeArguments();
    Object[] arguments =
        new Object
            [2 + parameters.length + receiverTypeArguments.size() + methodTypeArguments.length];
    arguments[0] = execution;
    arguments[1] = annotation;
    System.arraycopy(parameters, 0, arguments, 2, parameters.length);
    int offset = 2 + parameters.length;
    for (CoreType type : receiverTypeArguments) arguments[offset++] = type;
    System.arraycopy(methodTypeArguments, 0, arguments, offset, methodTypeArguments.length);
    return call.call(target.target(), arguments);
  }
}
