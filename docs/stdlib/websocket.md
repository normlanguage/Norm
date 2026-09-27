# WebSocket Client

`std.websocket` provides a WS/WSS client. [client.norm](../../norm/stdlib/std/websocket/client.norm) defines public types, parameters, and defaults.

Connect with `connectWebSocket`, reusing `std.http.Uri`, `HeaderMap`, `std.io.Bytes`, and `std.time.Duration`. `sendText` and `sendBinary` send complete messages; `receive` returns a `WebSocketEvent`. Asynchronous calls use the existing [Task](concurrency.md); do not wait for network operations on a UI thread.

## Lifecycle and flow

- Only one receive operation may be active per connection. Concurrent sends are serialized, and time spent waiting to send counts toward the operation timeout.
- The receive side assembles protocol fragments and reads the next event on demand. A complete message is bounded by `maximumMessageBytes`. Applications should keep receiving; an unconsumed event pauses delivery of later data and control events.
- The transport responds to Ping automatically. The response to an active `ping` is delivered as a Pong event; successful sending of Ping does not mean Pong has arrived.
- A receive timeout or cancellation does not consume the next message. A send that has started releases the connection on timeout or cancellation; its outcome cannot be treated as unexecuted.
- `finish` starts the close handshake, discards subsequent data, and waits within the budget for the peer to close. `close` releases the connection immediately for `use`, cancellation, and execution-scope cleanup; repeated closing does not release twice.
- A normal peer close returns a Closed event with status and reason. Connection, TLS, handshake, protocol, and capacity errors use `WebSocketException`.

The client does not reconnect automatically or replay messages. No WebSocket server is provided.

## Implementation and verification

| Responsibility | Entry point |
| --- | --- |
| Platform contract | [websocket](../../cli/compiler/src/main/java/dev/w0fv1/norm/platform/websocket) |
| JDK transport | [JdkWebSocketTransport](../../cli/compiler/src/main/java/dev/w0fv1/norm/platform/jdk/JdkWebSocketTransport.java), [JdkWebSocketConnection](../../cli/compiler/src/main/java/dev/w0fv1/norm/platform/jdk/JdkWebSocketConnection.java) |
| ABI | [stdlib-abi.json](../../cli/compiler/stdlib-abi.json) |
| Real network and TLS tests | [JdkWebSocketTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/platform/jdk/JdkWebSocketTest.java) |
| Norm calls and resource lifecycle | [WebSocketClientTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/stdlib/WebSocketClientTest.java) |
