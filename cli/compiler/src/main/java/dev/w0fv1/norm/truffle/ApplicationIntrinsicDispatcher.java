package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.w0fv1.norm.abi.IntrinsicId;
import java.io.IOException;
import java.util.ArrayList;

final class ApplicationIntrinsicDispatcher {
  private ApplicationIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return switch (intrinsic) {
      case APPLICATION_ARGUMENTS ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              new RuntimeValues.ListValue(type, new ArrayList<>(context.arguments()));
      case APPLICATION_ENVIRONMENT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            String value = context.environment().get((String) arguments[0]);
            return value == null ? RuntimeValues.NullValue.INSTANCE : value;
          };
      case APPLICATION_WORKING_DIRECTORY ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              context.workingDirectory().toString();
      case APPLICATION_EXIT_CODE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            context.setExitCode((Integer) arguments[0]);
            return null;
          };
      case CONSOLE_READ ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            try {
              byte[] content = read(context.input(), (Integer) arguments[0]);
              return content == null
                  ? RuntimeValues.NullValue.INSTANCE
                  : execution.values().bytes(new ByteSequence(content));
            } catch (IOException error) {
              throw execution.values().javaException(error, execution, location);
            }
          };
      case CONSOLE_WRITE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            var writer = (Boolean) arguments[1] ? context.error() : context.output();
            if (!write(writer, (String) arguments[0]))
              throw execution
                  .values()
                  .javaException(
                      new IOException("standard output write failed"), execution, location);
            return null;
          };
      default -> throw new IllegalStateException("unsupported application intrinsic " + intrinsic);
    };
  }

  @TruffleBoundary
  private static boolean write(java.io.PrintWriter writer, String text) {
    writer.print(text);
    writer.flush();
    return !writer.checkError();
  }

  @TruffleBoundary
  private static byte[] read(java.io.InputStream input, int maximumBytes) throws IOException {
    byte[] buffer = new byte[Math.min(maximumBytes, 8192)];
    int count = input.read(buffer);
    return count < 0 ? null : java.util.Arrays.copyOf(buffer, count);
  }
}
