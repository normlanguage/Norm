package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.PlatformInstant;
import dev.w0fv1.norm.platform.time.PlatformTimeException;
import dev.w0fv1.norm.platform.time.SystemClock;

final class SystemIntrinsicDispatcher {
  private SystemIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return switch (intrinsic) {
      case TIME_SYSTEM_CLOCK ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              systemClock(type, context, execution);
      case TIME_CLOCK_NOW ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              clockNow(arguments[0], type, execution, location);
      case SYSTEM_TIME_ZONE ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              context.timeZone().getId();
      case SYSTEM_SET_TIME_ZONE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            try {
              context.setTimeZone(java.time.ZoneId.of((String) arguments[0]));
            } catch (java.time.DateTimeException failure) {
              throw execution.values().javaException(failure, execution, location);
            }
            return null;
          };
      case SYSTEM_FORMAT_TIME ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              formatTime((Long) arguments[0], context);
      case SYSTEM_CURRENT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            if (type == null || execution == null) {
              throw new IllegalStateException("system runtime type is unavailable");
            }
            return execution.values().opaque(type, context, "System");
          };
      case SYSTEM_RUNTIME ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            if (type == null || execution == null) {
              throw new IllegalStateException("runtime capability type is unavailable");
            }
            return execution.values().opaque(type, context, "Runtime");
          };
      default -> throw new IllegalStateException("unsupported system intrinsic " + intrinsic);
    };
  }

  @TruffleBoundary
  private static String formatTime(long epochSecond, ExecutionContext context) {
    return java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        java.time.Instant.ofEpochSecond(epochSecond).atZone(context.timeZone()));
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
