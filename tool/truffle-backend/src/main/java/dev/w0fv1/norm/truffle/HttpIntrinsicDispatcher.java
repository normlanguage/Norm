package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.HttpMethod;
import dev.w0fv1.norm.execution.OperationControl;
import dev.w0fv1.norm.execution.PlatformDuration;
import dev.w0fv1.norm.execution.PlatformHttpException;
import dev.w0fv1.norm.execution.PlatformHttpHeader;
import dev.w0fv1.norm.execution.PlatformHttpRequest;
import dev.w0fv1.norm.execution.PlatformHttpResponse;
import dev.w0fv1.norm.execution.PlatformRead;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class HttpIntrinsicDispatcher {
  private HttpIntrinsicDispatcher() {}

  static Object execute(
      IntrinsicId intrinsic,
      Object[] arguments,
      CoreType type,
      ExecutionContext context,
      ExecutionState execution,
      Node location) {
    try {
      return switch (intrinsic) {
        case HTTP_SEND -> send(arguments, type, context, execution);
        case HTTP_RESPONSE_STATUS -> response(arguments[0]).statusCode();
        case HTTP_RESPONSE_HEADERS -> headers(arguments[0], type);
        case HTTP_RESPONSE_READ -> read(arguments[0], (Integer) arguments[1], execution, location);
        case HTTP_RESPONSE_CLOSE -> close(arguments[0]);
        default -> throw new IllegalStateException("unsupported HTTP intrinsic " + intrinsic);
      };
    } catch (PlatformHttpException failure) {
      if (execution == null) {
        throw new IllegalStateException("system exception runtime is unavailable", failure);
      }
      throw execution.values().httpException(failure, execution, location);
    } catch (ResourceCloseException failure) {
      if (failure.getCause() instanceof PlatformHttpException platformFailure) {
        if (execution == null) {
          throw new IllegalStateException("system exception runtime is unavailable", failure);
        }
        throw execution.values().httpException(platformFailure, execution, location);
      }
      throw failure;
    }
  }

  private static RuntimeValues.OpaqueResource send(
      Object[] arguments, CoreType type, ExecutionContext context, ExecutionState execution) {
    if (type == null || execution == null) {
      throw new IllegalStateException("HTTP response runtime type is unavailable");
    }
    PlatformHttpRequest request =
        new PlatformHttpRequest(
            HttpMethod.valueOf((String) arguments[0]),
            (String) arguments[1],
            requestHeaders(arguments[2]),
            body(arguments[3]));
    OperationControl control =
        new OperationControl(
            context.cancellation(),
            new PlatformDuration((Long) arguments[4], (Integer) arguments[5]));
    PlatformHttpResponse response = hostSend(context, request, control);
    return execution
        .values()
        .resource(type, response, "HttpResponse(" + request.uri() + ")", execution);
  }

  private static List<PlatformHttpHeader> requestHeaders(Object value) {
    if (!(value instanceof RuntimeValues.ArrayValue encoded) || encoded.values.size() % 2 != 0) {
      throw new IllegalStateException("HTTP header representation is invalid");
    }
    List<PlatformHttpHeader> headers = new ArrayList<>(encoded.values.size() / 2);
    for (int index = 0; index < encoded.values.size(); index += 2) {
      headers.add(
          new PlatformHttpHeader(
              (String) encoded.values.get(index), (String) encoded.values.get(index + 1)));
    }
    return List.copyOf(headers);
  }

  private static Optional<byte[]> body(Object value) {
    if (value == RuntimeValues.NullValue.INSTANCE) return Optional.empty();
    if (value instanceof RuntimeValues.OpaqueValue opaque
        && opaque.value instanceof ByteSequence bytes) {
      return Optional.of(
          java.util.Arrays.copyOfRange(
              bytes.storage(), bytes.offset(), bytes.offset() + bytes.size()));
    }
    throw new IllegalStateException("HTTP request body representation is invalid");
  }

  private static RuntimeValues.ArrayValue headers(Object value, CoreType type) {
    if (type == null) throw new IllegalStateException("HTTP header runtime type is unavailable");
    List<Object> encoded = new ArrayList<>();
    for (PlatformHttpHeader header : response(value).headers()) {
      encoded.add(header.name());
      encoded.add(header.value());
    }
    return new RuntimeValues.ArrayValue(type, encoded);
  }

  private static Object read(
      Object value, int maximumBytes, ExecutionState execution, Node location) {
    if (execution == null) throw new IllegalStateException("HTTP execution is unavailable");
    if (maximumBytes < 1) {
      throw new NormGuestException(
          RuntimeErrorCode.INVALID_ARGUMENT, "maximumBytes must be positive", location);
    }
    PlatformRead result = hostRead(response(value), maximumBytes);
    if (result == PlatformRead.Eof.INSTANCE) return RuntimeValues.NullValue.INSTANCE;
    PlatformRead.Data data = (PlatformRead.Data) result;
    return execution.values().bytes(new ByteSequence(data.storage(), 0, data.length()));
  }

  private static Object close(Object value) {
    hostClose(resource(value));
    return null;
  }

  private static PlatformHttpResponse response(Object value) {
    return resource(value).value(PlatformHttpResponse.class);
  }

  private static ManagedResource resource(Object value) {
    if (value instanceof RuntimeValues.OpaqueResource resource) return resource.resource;
    throw new IllegalStateException("HTTP response host value is unavailable");
  }

  @TruffleBoundary
  private static PlatformHttpResponse hostSend(
      ExecutionContext context, PlatformHttpRequest request, OperationControl control) {
    return context.platform().httpTransport().send(request, control);
  }

  @TruffleBoundary
  private static PlatformRead hostRead(PlatformHttpResponse response, int maximumBytes) {
    return response.read(maximumBytes);
  }

  @TruffleBoundary
  private static void hostClose(ManagedResource resource) {
    resource.close();
  }
}
