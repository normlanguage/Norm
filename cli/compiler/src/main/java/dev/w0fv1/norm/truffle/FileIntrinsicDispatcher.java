package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import dev.w0fv1.norm.platform.PlatformRead;
import dev.w0fv1.norm.platform.file.FileSyncMode;
import dev.w0fv1.norm.platform.file.FileWriteMode;
import dev.w0fv1.norm.platform.file.PlatformByteReader;
import dev.w0fv1.norm.platform.file.PlatformByteWriter;
import dev.w0fv1.norm.platform.file.PlatformFileException;

final class FileIntrinsicDispatcher {
  private FileIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    IntrinsicOperation operation =
        switch (intrinsic) {
          case FILE_OPEN_READ ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  openRead((String) arguments[0], type, context, execution);
          case FILE_READER_READ ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  read(arguments[0], (Integer) arguments[1], execution, location);
          case FILE_OPEN_WRITE ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  openWrite((String) arguments[0], (String) arguments[1], type, context, execution);
          case FILE_WRITER_WRITE ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  write(arguments[0], arguments[1]);
          case FILE_WRITER_FLUSH ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  flush(arguments[0]);
          case FILE_WRITER_SYNC ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  sync(arguments[0], (String) arguments[1]);
          case FILE_CLOSE ->
              (receiver, arguments, type, context, location, annotations, execution) ->
                  close(arguments[0]);
          default -> throw new IllegalStateException("unsupported file intrinsic " + intrinsic);
        };
    return (receiver, arguments, type, context, location, annotations, execution) -> {
      try {
        return operation.execute(
            receiver, arguments, type, context, location, annotations, execution);
      } catch (PlatformFileException failure) {
        if (execution == null) {
          throw new IllegalStateException("system exception runtime is unavailable", failure);
        }
        throw execution.values().fileException(failure, execution, location);
      } catch (ResourceCloseException failure) {
        if (failure.getCause() instanceof PlatformFileException platformFailure) {
          if (execution == null) {
            throw new IllegalStateException("system exception runtime is unavailable", failure);
          }
          throw execution.values().fileException(platformFailure, execution, location);
        }
        throw failure;
      }
    };
  }

  private static RuntimeValues.OpaqueResource openRead(
      String path, CoreType type, ExecutionContext context, ExecutionState execution) {
    requireRuntime(type, execution);
    PlatformByteReader reader = hostOpenRead(context, path);
    return execution.values().resource(type, reader, "FileReader(" + path + ")", execution);
  }

  private static Object read(
      Object value, int maximumBytes, ExecutionState execution, Node location) {
    if (execution == null) throw new IllegalStateException("file execution is unavailable");
    if (maximumBytes < 1) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "maximumBytes must be positive", location);
    }
    PlatformRead result = hostRead(reader(value), maximumBytes);
    if (result == PlatformRead.Eof.INSTANCE) return RuntimeValues.NullValue.INSTANCE;
    PlatformRead.Data data = (PlatformRead.Data) result;
    return execution.values().bytes(new ByteSequence(data.storage(), 0, data.length()));
  }

  private static RuntimeValues.OpaqueResource openWrite(
      String path, String mode, CoreType type, ExecutionContext context, ExecutionState execution) {
    requireRuntime(type, execution);
    PlatformByteWriter writer = hostOpenWrite(context, path, FileWriteMode.valueOf(mode));
    return execution.values().resource(type, writer, "FileWriter(" + path + ")", execution);
  }

  private static int write(Object value, Object content) {
    ByteSequence bytes = bytes(content);
    return hostWrite(writer(value), bytes.storage(), bytes.offset(), bytes.size());
  }

  private static Object flush(Object value) {
    hostFlush(writer(value));
    return null;
  }

  private static Object sync(Object value, String mode) {
    hostSync(writer(value), FileSyncMode.valueOf(mode));
    return null;
  }

  private static Object close(Object value) {
    hostClose(resource(value));
    return null;
  }

  private static void requireRuntime(CoreType type, ExecutionState execution) {
    if (type == null || execution == null) {
      throw new IllegalStateException("file runtime type is unavailable");
    }
  }

  private static PlatformByteReader reader(Object value) {
    return resource(value).value(PlatformByteReader.class);
  }

  private static PlatformByteWriter writer(Object value) {
    return resource(value).value(PlatformByteWriter.class);
  }

  private static ManagedResource resource(Object value) {
    if (value instanceof RuntimeValues.OpaqueResource resource) return resource.resource;
    throw new IllegalStateException("file resource host value is unavailable");
  }

  private static ByteSequence bytes(Object value) {
    if (value instanceof RuntimeValues.OpaqueValue opaque
        && opaque.value instanceof ByteSequence sequence) {
      return sequence;
    }
    throw new IllegalStateException("bytes host value is unavailable");
  }

  @TruffleBoundary
  private static PlatformByteReader hostOpenRead(ExecutionContext context, String path) {
    return context.platform().fileSystem().openRead(path);
  }

  @TruffleBoundary
  private static PlatformRead hostRead(PlatformByteReader reader, int maximumBytes) {
    return reader.read(maximumBytes);
  }

  @TruffleBoundary
  private static PlatformByteWriter hostOpenWrite(
      ExecutionContext context, String path, FileWriteMode mode) {
    return context.platform().fileSystem().openWrite(path, mode);
  }

  @TruffleBoundary
  private static int hostWrite(PlatformByteWriter writer, byte[] bytes, int offset, int length) {
    return writer.write(bytes, offset, length);
  }

  @TruffleBoundary
  private static void hostFlush(PlatformByteWriter writer) {
    writer.flush();
  }

  @TruffleBoundary
  private static void hostSync(PlatformByteWriter writer, FileSyncMode mode) {
    writer.sync(mode);
  }

  @TruffleBoundary
  private static void hostClose(ManagedResource resource) {
    resource.close();
  }
}
