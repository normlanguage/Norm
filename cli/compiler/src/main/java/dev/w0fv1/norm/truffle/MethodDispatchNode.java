package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.bridge.JavaApplicationBridge;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.DefinitionId;
import java.util.List;
import java.util.Map;

final class MethodDispatchNode extends Node {
  private final DefinitionId slot;
  private final Map<BuiltinTypeId, Map<DefinitionId, RuntimeValues.DispatchTarget>> builtinDispatch;
  @Child private IndirectCallNode call = IndirectCallNode.create();

  MethodDispatchNode(
      DefinitionId slot,
      Map<BuiltinTypeId, Map<DefinitionId, RuntimeValues.DispatchTarget>> builtinDispatch) {
    this.slot = slot;
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
    if (target instanceof RuntimeValues.DispatchTarget.HostMethod host) {
      Object hostReceiver =
          receiver instanceof RuntimeValues.OpaqueValue opaque ? opaque.value : receiver;
      return JavaApplicationBridge.invokeHost(
          hostReceiver, host.definition().toString(), arguments);
    }
    RuntimeValues.DispatchTarget.Callable callableTarget =
        (RuntimeValues.DispatchTarget.Callable) target;
    if (receiver instanceof RuntimeValues.ObjectValue object
        && object.dispatchToHost
        && object.hostValue != null) {
      return JavaApplicationBridge.invokeHost(object.hostValue, slot.toString(), arguments);
    }
    CoreType receiverType = RuntimeValues.runtimeType(receiver);
    List<CoreType> concreteReceiverArguments =
        receiverType instanceof CoreType.Declared declared ? declared.arguments() : List.of();
    List<CoreType> ownerTypeArguments =
        callableTarget.specializedReceiverTypeArguments()
            ? callableTarget.receiverTypeArguments().stream()
                .map(type -> type.substitute(concreteReceiverArguments::get))
                .toList()
            : concreteReceiverArguments;
    Object[] values =
        new Object[arguments.length + ownerTypeArguments.size() + methodTypeArguments.length + 2];
    values[0] = ExecutionContextAccess.state(frame);
    values[1] = receiver;
    System.arraycopy(arguments, 0, values, 2, arguments.length);
    for (int index = 0; index < ownerTypeArguments.size(); index++) {
      values[arguments.length + index + 2] = ownerTypeArguments.get(index);
    }
    System.arraycopy(
        methodTypeArguments,
        0,
        values,
        arguments.length + ownerTypeArguments.size() + 2,
        methodTypeArguments.length);
    return call.call(callableTarget.target(), values);
  }

  private RuntimeValues.DispatchTarget target(Object receiver) {
    if (receiver instanceof RuntimeValues.ObjectValue object) {
      RuntimeValues.DispatchTarget target = object.objectInfo.dispatch().get(slot);
      if (target != null) return target;
    } else if (receiver instanceof RuntimeValues.OpaqueValue opaque
        && opaque.aggregateInfo != null) {
      RuntimeValues.DispatchTarget target = opaque.aggregateInfo.dispatch().get(slot);
      if (target != null) return target;
    } else if (receiver instanceof RuntimeValues.OpaqueResource resource
        && resource.aggregateInfo != null) {
      RuntimeValues.DispatchTarget target = resource.aggregateInfo.dispatch().get(slot);
      if (target != null) return target;
    } else {
      Map<DefinitionId, RuntimeValues.DispatchTarget> table =
          builtinDispatch.get(RuntimeValues.builtinType(receiver));
      if (table != null) {
        RuntimeValues.DispatchTarget target = table.get(slot);
        if (target != null) return target;
      }
    }
    throw new IllegalStateException("verified method dispatch slot is absent");
  }
}
