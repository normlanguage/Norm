package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.truffle.RuntimeValues.Closure;
import dev.w0fv1.norm.truffle.RuntimeValues.DispatchTarget;
import dev.w0fv1.norm.truffle.RuntimeValues.ObjectValue;
import java.util.List;
import java.util.Objects;

final class RuntimeInvocation {
  private RuntimeInvocation() {}

  static Object invoke(ExecutionState execution, Closure closure, Object... arguments) {
    PreparedInvocation invocation = prepareInvocation(execution, closure, arguments);
    return invocation.target().call(invocation.arguments());
  }

  @TruffleBoundary
  static PreparedInvocation prepareInvocation(
      ExecutionState execution, Closure closure, Object... arguments) {
    CallTarget target = closure.target();
    Object receiver = closure.receiver();
    Object[] callableArguments = arguments;
    Object[] receiverTypeArguments = closure.receiverTypeArguments();
    if (closure.unbound()) {
      if (arguments.length == 0 || !(arguments[0] instanceof ObjectValue object)) {
        throw new IllegalArgumentException("unbound method requires an object receiver");
      }
      receiver = object;
      callableArguments = java.util.Arrays.copyOfRange(arguments, 1, arguments.length);
      if (closure.virtualSlot() != null) {
        DispatchTarget targetValue = object.objectInfo.dispatch().get(closure.virtualSlot());
        if (!(targetValue instanceof DispatchTarget.Callable dispatch)) {
          throw new IllegalStateException("virtual method dispatch target is absent");
        }
        target = dispatch.target();
        List<CoreType> concreteArguments =
            object.type instanceof CoreType.Declared declared ? declared.arguments() : List.of();
        receiverTypeArguments =
            dispatch.receiverTypeArguments().stream()
                .map(type -> type.substitute(concreteArguments::get))
                .toArray();
      }
    }
    int receiverCount = receiver == null ? 0 : 1;
    int ownerTypeArgumentCount = receiverTypeArguments.length;
    Object[] complete =
        new Object
            [1
                + receiverCount
                + closure.captures().length
                + callableArguments.length
                + ownerTypeArgumentCount
                + closure.reifiedArguments().length];
    complete[0] = execution;
    int offset = 1;
    if (receiverCount == 1) complete[offset++] = receiver;
    System.arraycopy(closure.captures(), 0, complete, offset, closure.captures().length);
    offset += closure.captures().length;
    System.arraycopy(callableArguments, 0, complete, offset, callableArguments.length);
    offset += callableArguments.length;
    System.arraycopy(receiverTypeArguments, 0, complete, offset, ownerTypeArgumentCount);
    offset += ownerTypeArgumentCount;
    System.arraycopy(
        closure.reifiedArguments(), 0, complete, offset, closure.reifiedArguments().length);
    return new PreparedInvocation(target, complete);
  }

  record PreparedInvocation(CallTarget target, Object[] arguments) {
    PreparedInvocation {
      Objects.requireNonNull(target, "target");
      arguments = arguments.clone();
    }
  }
}
