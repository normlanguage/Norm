package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.OperationControl;
import dev.w0fv1.norm.platform.PlatformDuration;
import dev.w0fv1.norm.platform.http.PlatformHttpHeader;
import dev.w0fv1.norm.platform.websocket.PlatformWebSocket;
import dev.w0fv1.norm.platform.websocket.PlatformWebSocketException;
import dev.w0fv1.norm.platform.websocket.WebSocketEvent;
import dev.w0fv1.norm.platform.websocket.WebSocketFailure;
import dev.w0fv1.norm.platform.websocket.WebSocketOperation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class WebSocketIntrinsicDispatcher {
  private WebSocketIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    IntrinsicOperation operation =
        switch (intrinsic) {
          case WS_CONNECT ->
              (receiver, args, type, context, location, annotations, execution) ->
                  connect(args, type, context, execution);
          case WS_SUBPROTOCOL ->
              (receiver, args, type, context, location, annotations, execution) ->
                  socket(args[0]).subprotocol();
          case WS_SEND_TEXT ->
              (receiver, args, type, context, location, annotations, execution) -> {
                sendText(
                    socket(args[0]),
                    (String) args[1],
                    control(context, args, 2, WebSocketOperation.SEND),
                    execution.callbacks());
                return null;
              };
          case WS_SEND_BINARY ->
              (receiver, args, type, context, location, annotations, execution) -> {
                sendBinary(
                    socket(args[0]),
                    bytes(args[1]),
                    control(context, args, 2, WebSocketOperation.SEND),
                    execution.callbacks());
                return null;
              };
          case WS_PING ->
              (receiver, args, type, context, location, annotations, execution) -> {
                ping(
                    socket(args[0]),
                    bytes(args[1]),
                    control(context, args, 2, WebSocketOperation.SEND),
                    execution.callbacks());
                return null;
              };
          case WS_RECEIVE ->
              (receiver, args, type, context, location, annotations, execution) ->
                  event(
                      receive(
                          socket(args[0]),
                          control(context, args, 1, WebSocketOperation.RECEIVE),
                          execution.callbacks()),
                      type,
                      execution);
          case WS_FINISH ->
              (receiver, args, type, context, location, annotations, execution) -> {
                finish(
                    socket(args[0]),
                    (Integer) args[1],
                    (String) args[2],
                    control(context, args, 3, WebSocketOperation.CLOSE),
                    execution.callbacks());
                close(args[0]);
                return null;
              };
          case WS_CLOSE ->
              (receiver, args, type, context, location, annotations, execution) -> {
                close(args[0]);
                return null;
              };
          default ->
              throw new IllegalStateException("unsupported WebSocket intrinsic " + intrinsic);
        };
    return (receiver, args, type, context, location, annotations, execution) -> {
      try {
        return operation.execute(receiver, args, type, context, location, annotations, execution);
      } catch (PlatformWebSocketException error) {
        throw execution.values().webSocketException(error, execution, location);
      }
    };
  }

  private static Object connect(
      Object[] args, CoreType type, ExecutionContext context, ExecutionState execution) {
    var encoded = (RuntimeValues.ArrayValue) args[1];
    List<PlatformHttpHeader> headers = new ArrayList<>();
    for (int i = 0; i < encoded.values.size(); i += 2)
      headers.add(
          new PlatformHttpHeader(
              (String) encoded.values.get(i), (String) encoded.values.get(i + 1)));
    var protocols =
        ((RuntimeValues.ArrayValue) args[2]).values.stream().map(String.class::cast).toList();
    var socket =
        connect(
            context,
            (String) args[0],
            headers,
            protocols,
            (Integer) args[3],
            control(context, args, 4, WebSocketOperation.CONNECT),
            execution.callbacks());
    return execution.values().resource(type, socket, "WebSocket", execution);
  }

  private static OperationControl control(
      ExecutionContext context, Object[] args, int offset, WebSocketOperation operation) {
    if ((Long) args[offset] < 0 || (Long) args[offset] == 0 && (Integer) args[offset + 1] == 0) {
      String uri = args[0] instanceof String value ? value : socket(args[0]).uri();
      throw new PlatformWebSocketException(
          operation, WebSocketFailure.INVALID_REQUEST, uri, "timeout must be positive", null);
    }
    return new OperationControl(
        context.cancellation(),
        new PlatformDuration((Long) args[offset], (Integer) args[offset + 1]));
  }

  private static byte[] bytes(Object value) {
    var sequence = (ByteSequence) ((RuntimeValues.OpaqueValue) value).value;
    return Arrays.copyOfRange(
        sequence.storage(), sequence.offset(), sequence.offset() + sequence.size());
  }

  private static PlatformWebSocket socket(Object value) {
    return ((RuntimeValues.OpaqueResource) value).resource.value(PlatformWebSocket.class);
  }

  private static Object event(WebSocketEvent event, CoreType type, ExecutionState execution) {
    return switch (event) {
      case WebSocketEvent.Text value ->
          execution.values().enumValue(type, "Text", List.of(value.text()));
      case WebSocketEvent.Binary value ->
          execution
              .values()
              .enumValue(
                  type,
                  "Binary",
                  List.of(
                      execution
                          .values()
                          .bytes(new ByteSequence(value.data(), 0, value.data().length))));
      case WebSocketEvent.Pong value ->
          execution
              .values()
              .enumValue(
                  type,
                  "Pong",
                  List.of(
                      execution
                          .values()
                          .bytes(new ByteSequence(value.data(), 0, value.data().length))));
      case WebSocketEvent.Closed value ->
          execution.values().enumValue(type, "Closed", List.of(value.code(), value.reason()));
    };
  }

  @TruffleBoundary
  private static PlatformWebSocket connect(
      ExecutionContext context,
      String uri,
      List<PlatformHttpHeader> headers,
      List<String> protocols,
      int maximumMessageBytes,
      OperationControl control,
      GuestCallbackScheduler callbacks) {
    return callbacks.hostCall(
        () ->
            context
                .platform()
                .webSocketTransport()
                .connect(uri, headers, protocols, maximumMessageBytes, control));
  }

  @TruffleBoundary
  private static void sendText(
      PlatformWebSocket socket,
      String text,
      OperationControl control,
      GuestCallbackScheduler callbacks) {
    callbacks.hostCall(
        () -> {
          socket.sendText(text, control);
          return null;
        });
  }

  @TruffleBoundary
  private static void sendBinary(
      PlatformWebSocket socket,
      byte[] data,
      OperationControl control,
      GuestCallbackScheduler callbacks) {
    callbacks.hostCall(
        () -> {
          socket.sendBinary(data, control);
          return null;
        });
  }

  @TruffleBoundary
  private static void ping(
      PlatformWebSocket socket,
      byte[] data,
      OperationControl control,
      GuestCallbackScheduler callbacks) {
    callbacks.hostCall(
        () -> {
          socket.ping(data, control);
          return null;
        });
  }

  @TruffleBoundary
  private static WebSocketEvent receive(
      PlatformWebSocket socket, OperationControl control, GuestCallbackScheduler callbacks) {
    return callbacks.hostCall(() -> socket.receive(control));
  }

  @TruffleBoundary
  private static void finish(
      PlatformWebSocket socket,
      int code,
      String reason,
      OperationControl control,
      GuestCallbackScheduler callbacks) {
    callbacks.hostCall(
        () -> {
          socket.finish(code, reason, control);
          return null;
        });
  }

  @TruffleBoundary
  private static void close(Object value) {
    ((RuntimeValues.OpaqueResource) value).resource.close();
  }
}
