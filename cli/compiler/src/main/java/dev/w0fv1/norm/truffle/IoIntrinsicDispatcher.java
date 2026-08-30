package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class IoIntrinsicDispatcher {
  private IoIntrinsicDispatcher() {}

  static Object execute(
      IntrinsicId intrinsic,
      Object first,
      Object second,
      Object third,
      CoreType type,
      ExecutionState execution,
      Node location) {
    return switch (intrinsic) {
      case IO_BYTES_CREATE -> create(first, type, execution);
      case IO_BYTES_SIZE -> bytes(first).size();
      case IO_BYTES_AT -> at(first, (Integer) second, location);
      case IO_BYTES_SLICE ->
          slice(first, (Integer) second, (Integer) third, type, execution, location);
      case IO_BYTES_TO_ARRAY -> toArray(first, type);
      case IO_BYTES_JOIN -> join(first, type, execution);
      case IO_TEXT_ENCODE_UTF8 -> encodeUtf8((String) first, execution);
      case IO_TEXT_DECODE_UTF8 -> decodeUtf8(first);
      case IO_USE -> use(first, second, execution);
      default -> throw new IllegalStateException("unsupported io intrinsic " + intrinsic);
    };
  }

  private static RuntimeValues.OpaqueValue create(
      Object value, CoreType type, ExecutionState execution) {
    if (type == null || execution == null) {
      throw new IllegalStateException("bytes runtime type is unavailable");
    }
    List<Object> values = ((RuntimeValues.ArrayValue) value).values;
    byte[] storage = new byte[values.size()];
    for (int index = 0; index < values.size(); index++) {
      storage[index] = (byte) ((Integer) values.get(index)).intValue();
    }
    return execution.values().opaque(type, new ByteSequence(storage), "Bytes");
  }

  private static int at(Object value, int index, Node location) {
    try {
      return bytes(value).at(index);
    } catch (IndexOutOfBoundsException exception) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "byte index is outside the sequence", location);
    }
  }

  private static RuntimeValues.OpaqueValue slice(
      Object value, int start, int length, CoreType type, ExecutionState execution, Node location) {
    if (type == null || execution == null) {
      throw new IllegalStateException("bytes runtime type is unavailable");
    }
    try {
      return execution.values().opaque(type, bytes(value).slice(start, length), "Bytes");
    } catch (IndexOutOfBoundsException exception) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "byte slice is outside the sequence", location);
    }
  }

  private static RuntimeValues.ArrayValue toArray(Object value, CoreType type) {
    if (type == null) throw new IllegalStateException("byte array runtime type is unavailable");
    byte[] storage = bytes(value).toArray();
    java.util.ArrayList<Object> values = new java.util.ArrayList<>(storage.length);
    for (byte item : storage) values.add(Byte.toUnsignedInt(item));
    return new RuntimeValues.ArrayValue(type, values);
  }

  private static RuntimeValues.OpaqueValue join(
      Object value, CoreType type, ExecutionState execution) {
    if (type == null || execution == null) {
      throw new IllegalStateException("bytes runtime type is unavailable");
    }
    List<Object> values = ((RuntimeValues.ListValue) value).values;
    long total = 0;
    for (Object item : values) total += bytes(item).size();
    if (total > Integer.MAX_VALUE) {
      throw new IllegalStateException("byte sequence exceeds the runtime array limit");
    }
    byte[] storage = new byte[(int) total];
    int offset = 0;
    for (Object item : values) {
      ByteSequence sequence = bytes(item);
      sequence.copyTo(storage, offset);
      offset += sequence.size();
    }
    return execution.values().opaque(type, new ByteSequence(storage), "Bytes");
  }

  @TruffleBoundary
  private static Object encodeUtf8(String text, ExecutionState execution) {
    if (execution == null) throw new IllegalStateException("text execution is unavailable");
    try {
      ByteBuffer encoded =
          StandardCharsets.UTF_8
              .newEncoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .encode(CharBuffer.wrap(text));
      byte[] storage = new byte[encoded.remaining()];
      encoded.get(storage);
      return execution.values().bytes(new ByteSequence(storage));
    } catch (CharacterCodingException failure) {
      return RuntimeValues.NullValue.INSTANCE;
    }
  }

  @TruffleBoundary
  private static Object decodeUtf8(Object content) {
    ByteSequence bytes = bytes(content);
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes.storage(), bytes.offset(), bytes.size()))
          .toString();
    } catch (CharacterCodingException failure) {
      return RuntimeValues.NullValue.INSTANCE;
    }
  }

  private static Object use(Object close, Object body, ExecutionState execution) {
    if (execution == null) throw new IllegalStateException("resource execution is unavailable");
    Object result;
    try {
      result = RuntimeValues.invoke(execution, closure(body));
    } catch (RuntimeException | Error failure) {
      try {
        RuntimeValues.invoke(execution, closure(close));
      } catch (RuntimeException | Error closeFailure) {
        if (closeFailure != failure) failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
    RuntimeValues.invoke(execution, closure(close));
    return result;
  }

  private static RuntimeValues.Closure closure(Object value) {
    if (value instanceof RuntimeValues.Closure closure) return closure;
    throw new IllegalStateException("resource callback is unavailable");
  }

  private static ByteSequence bytes(Object value) {
    if (value instanceof RuntimeValues.OpaqueValue opaque
        && opaque.value instanceof ByteSequence sequence) {
      return sequence;
    }
    throw new IllegalStateException("bytes host value is unavailable");
  }
}
