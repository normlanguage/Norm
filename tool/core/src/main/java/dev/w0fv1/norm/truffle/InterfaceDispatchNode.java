package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.DefinitionId;
import java.util.Map;

final class InterfaceDispatchNode extends Node {
  private final DefinitionId requirement;
  private final Map<BuiltinTypeId, Map<DefinitionId, RuntimeValues.DispatchTarget>> builtinDispatch;
  @Child private IndirectCallNode call = IndirectCallNode.create();

  InterfaceDispatchNode(
      DefinitionId requirement,
      Map<BuiltinTypeId, Map<DefinitionId, RuntimeValues.DispatchTarget>> builtinDispatch) {
    this.requirement = requirement;
    this.builtinDispatch = builtinDispatch;
  }

  Object execute(
      VirtualFrame frame,
      Object receiver,
      Object[] arguments,
      Object[] methodTypeArguments,
      Node location) {
    RuntimeValues.DispatchTarget target = target(receiver);
    if (target instanceof RuntimeValues.DispatchTarget.Intrinsic intrinsic) {
      return IntrinsicDispatcher.execute(
          intrinsic.intrinsic(),
          receiver,
          arguments,
          null,
          ExecutionContextAccess.get(frame),
          location);
    }
    RuntimeValues.ObjectValue object = (RuntimeValues.ObjectValue) receiver;
    int ownerTypeArgumentCount =
        object.type instanceof CoreType.Declared declared ? declared.arguments().size() : 0;
    Object[] values =
        new Object[arguments.length + ownerTypeArgumentCount + methodTypeArguments.length + 2];
    values[0] = ExecutionContextAccess.get(frame);
    values[1] = receiver;
    System.arraycopy(arguments, 0, values, 2, arguments.length);
    if (object.type instanceof CoreType.Declared declared) {
      for (int index = 0; index < declared.arguments().size(); index++) {
        values[arguments.length + index + 2] = declared.arguments().get(index);
      }
    }
    System.arraycopy(
        methodTypeArguments,
        0,
        values,
        arguments.length + ownerTypeArgumentCount + 2,
        methodTypeArguments.length);
    CallTarget callable = ((RuntimeValues.DispatchTarget.Callable) target).target();
    return call.call(callable, values);
  }

  private RuntimeValues.DispatchTarget target(Object receiver) {
    if (receiver instanceof RuntimeValues.ObjectValue object) {
      RuntimeValues.DispatchTarget target = object.classInfo.dispatch().get(requirement);
      if (target != null) return target;
    } else {
      Map<DefinitionId, RuntimeValues.DispatchTarget> table =
          builtinDispatch.get(RuntimeValues.builtinType(receiver));
      if (table != null) {
        RuntimeValues.DispatchTarget target = table.get(requirement);
        if (target != null) return target;
      }
    }
    throw new IllegalStateException("verified interface witness is absent");
  }
}
