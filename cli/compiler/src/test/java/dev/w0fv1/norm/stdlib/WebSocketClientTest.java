package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;
import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.testing.WebSocketTestServer;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
final class WebSocketClientTest {
  @Test
  void rejectsSignedNegativeOperationTimeoutsAsTypedExceptions() throws Exception {
    try (var server = new WebSocketTestServer()) {
      assertOutput(
          JdkSystemPlatform.standard(),
          """
          import std.websocket.connectWebSocket
          import std.websocket.WebSocketException
          import std.http.Uri
          import std.time.duration
          Void main() {
            var socket = connectWebSocket(uri: Uri(value: "%s"), timeout: duration(seconds: 5, nanoseconds: 0))
            var negative = duration(seconds: -1, nanoseconds: 0)
            try { socket.sendText(text: "must not send", timeout: negative) }
            catch WebSocketException error { printLine(error.reason) printLine(error.operation) }
            try { socket.receive(timeout: negative) }
            catch WebSocketException error { printLine(error.reason) printLine(error.operation) }
            try { socket.finish(code: 1000, reason: "must not close", timeout: negative) }
            catch WebSocketException error { printLine(error.reason) printLine(error.operation) }
            socket.finish(code: 1000, reason: "done", timeout: duration(seconds: 5, nanoseconds: 0))
          }
          """
              .formatted(server.uri()),
          "WebSocketFailure.InvalidRequest",
          "WebSocketOperation.Send",
          "WebSocketFailure.InvalidRequest",
          "WebSocketOperation.Receive",
          "WebSocketFailure.InvalidRequest",
          "WebSocketOperation.Close");
    }
  }

  @Test
  void exchangesMessagesAndClosesUsingTheStandardLibrary() throws Exception {
    try (var server = new WebSocketTestServer()) {
      assertOutput(
          JdkSystemPlatform.standard(),
          """
          import std.websocket.connectWebSocket
          import std.websocket.WebSocketEvent
          import std.http.Uri
              import std.io.bytes
              import std.io.Bytes
          import std.time.duration
          Void main() {
            var timeout = duration(seconds: 5, nanoseconds: 0)
            var socket = connectWebSocket(uri: Uri(value: "%s"), timeout: timeout)
            socket.sendText(text: "fragment", timeout: timeout)
            switch socket.receive(timeout: timeout) {
              case Text(String text) { printLine(text) }
                  case Binary(_) { printLine("unexpected binary") }
              case Pong(_) { printLine("unexpected pong") }
              case Closed(_, _) { printLine("unexpected close") }
            }
            socket.sendBinary(data: bytes(values: [0, 255]), timeout: timeout)
            switch socket.receive(timeout: timeout) {
                  case Binary(Bytes data) { printLine(data.size()) printLine(data.at(index: 1)) }
              case Text(String text) { printLine("unexpected text") }
              case Pong(_) { printLine("unexpected pong") }
              case Closed(_, _) { printLine("unexpected close") }
            }
            socket.finish(code: 1000, reason: "done", timeout: timeout)
            socket.close()
          }
          """
              .formatted(server.uri()),
          "你好",
          "2",
          "255");
      assertEquals(1000, server.closed.get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void exposesTypedFailuresAndKeepsTheConnectionAfterReceiveTimeout() throws Exception {
    try (var server = new WebSocketTestServer()) {
      assertOutput(
          JdkSystemPlatform.standard(),
          """
          import std.websocket.connectWebSocket
          import std.websocket.WebSocketException
          import std.http.Uri
          import std.time.duration
          Void main() {
            var socket = connectWebSocket(uri: Uri(value: "%s"), timeout: duration(seconds: 5, nanoseconds: 0))
            try { socket.receive(timeout: duration(seconds: 0, nanoseconds: 10000000)) }
            catch WebSocketException error {
              printLine(error.code)
              printLine(error.operation)
              printLine(error.reason)
            }
            socket.close()
          }
          """
              .formatted(server.uri()),
          "NORM-WS-TIMEOUT",
          "WebSocketOperation.Receive",
          "WebSocketFailure.Timeout");
    }
  }

  @Test
  void closesUnreleasedConnectionsAtExecutionBoundary() throws Exception {
    try (var server = new WebSocketTestServer()) {
      assertOutput(
          JdkSystemPlatform.standard(),
          """
          import std.websocket.connectWebSocket
          import std.http.Uri
          import std.time.duration
          Void main() {
            var socket = connectWebSocket(uri: Uri(value: "%s"), timeout: duration(seconds: 5, nanoseconds: 0))
            printLine("connected")
          }
          """
              .formatted(server.uri()),
          "connected");
      assertNotNull(server.closed.get(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void doesNotExposeHostIntrinsics() {
    assertFalse(compile("Void main() { __wsClose(socket: null) }").isSuccess());
  }
}
