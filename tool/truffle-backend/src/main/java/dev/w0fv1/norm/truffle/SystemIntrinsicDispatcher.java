package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.PlatformInstant;
import dev.w0fv1.norm.execution.PlatformTimeException;
import dev.w0fv1.norm.execution.SystemClock;

final class SystemIntrinsicDispatcher {
  private SystemIntrinsicDispatcher() {}

  static Object execute(
      IntrinsicId intrinsic,
      Object first,
      Object second,
      CoreType type,
      ExecutionContext context,
      ExecutionState execution,
      Node location) {
    return switch (intrinsic) {
      case TIME_SYSTEM_CLOCK -> systemClock(type, context, execution);
      case TIME_CLOCK_NOW -> clockNow(first, type, execution, location);
      default -> throw new IllegalStateException("unsupported system intrinsic " + intrinsic);
    };
  }

  private static RuntimeValues.OpaqueValue systemClock(
      CoreType type, ExecutionContext context, ExecutionState execution) {
    if (type == null || execution == null) {
      throw new IllegalStateException("system clock runtime type is unavailable");
    }
    return execution.values().opaque(type, context.platform().clock(), "Clock");
  }

  private static RuntimeValues.ObjectValue clockNow(
      Object value, CoreType type, ExecutionState execution, Node location) {
    if (type == null || execution == null) {
      throw new IllegalStateException("instant runtime type is unavailable");
    }
    if (!(value instanceof RuntimeValues.OpaqueValue opaque)
        || !(opaque.value instanceof SystemClock clock)) {
      throw new IllegalStateException("clock host value is unavailable");
    }
    try {
      PlatformInstant instant = hostNow(clock);
      return execution
          .values()
          .construct(type, execution, instant.epochSecond(), instant.nanosecond());
    } catch (PlatformTimeException failure) {
      throw execution.values().timeException(failure, execution, location);
    }
  }

  @TruffleBoundary
  private static PlatformInstant hostNow(SystemClock clock) {
    return clock.now();
  }
}
