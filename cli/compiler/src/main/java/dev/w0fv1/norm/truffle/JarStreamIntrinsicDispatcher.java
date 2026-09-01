package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class JarStreamIntrinsicDispatcher {
  private JarStreamIntrinsicDispatcher() {}

  static Object execute(
      IntrinsicId intrinsic,
      Object first,
      Object second,
      CoreType type,
      ExecutionState execution,
      Node location) {
    try {
      return switch (intrinsic) {
        case JAR_INPUT_STREAM_READ -> read(first, (Integer) second, type, execution, location);
        case JAR_OUTPUT_STREAM_WRITE -> write(first, second);
        case JAR_OUTPUT_STREAM_FLUSH -> flush(first);
        case JAR_STREAM_CLOSE -> close(first);
        default -> throw new IllegalStateException("unsupported JAR stream intrinsic " + intrinsic);
      };
    } catch (IOException failure) {
      if (execution == null) {
        throw new IllegalStateException("JAR stream exception runtime is unavailable", failure);
      }
      throw execution.values().javaException(failure, execution, location);
    } catch (ResourceCloseException failure) {
      if (execution == null) {
        throw new IllegalStateException("JAR stream exception runtime is unavailable", failure);
      }
      throw execution.values().javaException(failure.getCause(), execution, location);
    }
  }

  private static Object read(
      Object value, int maximumBytes, CoreType type, ExecutionState execution, Node location)
      throws IOException {
    if (type == null || execution == null) {
      throw new IllegalStateException("JAR input stream runtime is unavailable");
    }
    if (maximumBytes < 1) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "maximumBytes must be positive", location);
    }
    byte[] content = hostRead(resource(value).value(InputStream.class), maximumBytes);
    if (content.length == 0) return RuntimeValues.NullValue.INSTANCE;
    return execution.values().bytes(new ByteSequence(content));
  }

  private static int write(Object value, Object content) throws IOException {
    ByteSequence bytes = bytes(content);
    hostWrite(
        resource(value).value(OutputStream.class), bytes.storage(), bytes.offset(), bytes.size());
    return bytes.size();
  }

  private static Object flush(Object value) throws IOException {
    hostFlush(resource(value).value(OutputStream.class));
    return null;
  }

  private static Object close(Object value) {
    resource(value).close();
    return null;
  }

  private static ManagedResource resource(Object value) {
    if (value instanceof RuntimeValues.OpaqueResource resource) return resource.resource;
    throw new IllegalStateException("JAR stream resource host value is unavailable");
  }

  private static ByteSequence bytes(Object value) {
    if (value instanceof RuntimeValues.OpaqueValue opaque
        && opaque.value instanceof ByteSequence sequence) {
      return sequence;
    }
    throw new IllegalStateException("bytes host value is unavailable");
  }

  @TruffleBoundary
  private static byte[] hostRead(InputStream input, int maximumBytes) throws IOException {
    return input.readNBytes(maximumBytes);
  }

  @TruffleBoundary
  private static void hostWrite(OutputStream output, byte[] bytes, int offset, int length)
      throws IOException {
    output.write(bytes, offset, length);
  }

  @TruffleBoundary
  private static void hostFlush(OutputStream output) throws IOException {
    output.flush();
  }
}
