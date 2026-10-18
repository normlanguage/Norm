package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.w0fv1.norm.truffle.RuntimeValues.Closure;
import dev.w0fv1.norm.truffle.RuntimeValues.ObjectValue;
import java.util.Objects;

final class RuntimeInvocation {
  private static final ThreadLocal<ResultBinding> RESULT_BINDING = new ThreadLocal<>();

  private RuntimeInvocation() {}

  static Object invoke(ExecutionState execution, Closure closure, Object... arguments) {
    PreparedInvocation invocation = prepareInvocation(execution, closure, arguments);
    var previous = RESULT_BINDING.get();
    RESULT_BINDING.set(new ResultBinding(invocation.target(), closure.snapshotVersion()));
    try {
      return invocation.target().call(invocation.arguments());
    } finally {
      if (previous == null) RESULT_BINDING.remove();
      else RESULT_BINDING.set(previous);
    }
  }

  static LiveBindings.Version resultVersion(CallTarget target, LiveBindings.Version caller) {
    var binding = RESULT_BINDING.get();
    if (binding == null || binding.target != target || binding.consumed) return caller;
    binding.consumed = true;
    return binding.version == null ? caller : binding.version;
  }

  static Object adaptResult(ExecutionState execution, Object result, LiveBindings.Version version) {
    try (var scope = execution.liveBindings().pin(version);
        var transaction = RuntimeStateTransaction.open(execution)) {
      var adapted = transaction.transform(result, dev.w0fv1.norm.core.CoreType.DYNAMIC);
      transaction.commit();
      return adapted;
    }
  }

  private static final class ResultBinding {
    private final CallTarget target;
    private final LiveBindings.Version version;
    private boolean consumed;

    private ResultBinding(CallTarget target, LiveBindings.Version version) {
      this.target = target;
      this.version = version;
    }
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
    }
    int receiverCount = receiver == null ? 0 : 1;
    int ownerTypeArgumentCount = receiverTypeArguments.length;
    var constructor =
        closure
            .sourceDeclaration()
            .flatMap(occurrence -> closure.annotations().constructorParameters(occurrence));
    if (constructor.isPresent()) {
      var types = new Object[ownerTypeArgumentCount + closure.reifiedArguments().length];
      System.arraycopy(receiverTypeArguments, 0, types, 0, ownerTypeArgumentCount);
      System.arraycopy(
          closure.reifiedArguments(),
          0,
          types,
          ownerTypeArgumentCount,
          closure.reifiedArguments().length);
      return new PreparedInvocation(
          target,
          new Object[] {
            execution,
            receiver,
            ConstructionArguments.supplied(
                constructor.orElseThrow().count(),
                java.util.stream.IntStream.range(0, callableArguments.length).toArray(),
                callableArguments,
                types)
          });
    }
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
