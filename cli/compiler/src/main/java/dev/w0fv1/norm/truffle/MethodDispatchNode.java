package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
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
      return intrinsic
          .operation()
          .execute(receiver, arguments, null, ExecutionContextAccess.get(frame), location);
    }
    if (target instanceof RuntimeValues.DispatchTarget.HostMethod host) {
      Object hostReceiver =
          receiver instanceof RuntimeValues.OpaqueValue opaque ? opaque.value : receiver;
      return invokeHost(hostReceiver, host.definition(), arguments);
    }
    RuntimeValues.DispatchTarget.Callable callableTarget =
        (RuntimeValues.DispatchTarget.Callable) target;
    if (receiver instanceof RuntimeValues.ObjectValue object
        && object.dispatchToHost
        && object.hostValue != null) {
      return invokeHost(object.hostValue, slot, arguments);
    }
    CoreType receiverType = RuntimeValues.runtimeType(receiver);
    Object[] ownerTypeArguments = ownerTypeArguments(callableTarget, receiverType);
    Object[] values =
        new Object[arguments.length + ownerTypeArguments.length + methodTypeArguments.length + 2];
    values[0] = ExecutionContextAccess.state(frame);
    values[1] = receiver;
    System.arraycopy(arguments, 0, values, 2, arguments.length);
    System.arraycopy(
        ownerTypeArguments, 0, values, arguments.length + 2, ownerTypeArguments.length);
    System.arraycopy(
        methodTypeArguments,
        0,
        values,
        arguments.length + ownerTypeArguments.length + 2,
        methodTypeArguments.length);
    return call.call(callableTarget.target(), values);
  }

  @TruffleBoundary
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

  @TruffleBoundary
  private static Object invokeHost(Object receiver, DefinitionId definition, Object[] arguments) {
    return JavaApplicationBridge.invokeHost(receiver, definition.toString(), arguments);
  }

  @TruffleBoundary
  private static Object[] ownerTypeArguments(
      RuntimeValues.DispatchTarget.Callable target, CoreType receiverType) {
    List<CoreType> concrete =
        receiverType instanceof CoreType.Declared declared ? declared.arguments() : List.of();
    if (!target.specializedReceiverTypeArguments()) return concrete.toArray();
    return target.receiverTypeArguments().stream()
        .map(type -> type.substitute(concrete::get))
        .toArray();
  }
}
