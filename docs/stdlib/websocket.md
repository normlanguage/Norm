# WebSocket 客户端

`std.websocket` 提供 WS/WSS 客户端。公开类型、参数和默认值以 [client.norm](../../norm/stdlib/std/websocket/client.norm) 为准。

连接使用 `connectWebSocket`，复用 `std.http.Uri`、`HeaderMap`、`std.io.Bytes` 和 `std.time.Duration`。通过 `sendText`、`sendBinary` 发送完整消息，`receive` 返回 `WebSocketEvent`。异步调用使用现有 [Task](concurrency.md)，不在 UI 线程等待网络操作。

## 生命周期与流量

- 每条连接只允许一个活动接收操作；并发发送串行执行，等待发送的时间计入操作超时。
- 接收侧合并协议分片，按需读取下一个事件；完整消息受 `maximumMessageBytes` 限制。应用应持续接收，未消费事件会暂停后续数据和控制事件的交付。
- Ping 由传输实现自动应答；主动 `ping` 的响应以 Pong 事件交付。发送 Ping 成功不代表已经收到 Pong。
- 接收超时或取消不会消费下一条消息；已开始的发送超时或取消会释放连接，发送结果不能视为未执行。
- `finish` 发起关闭握手，丢弃后续数据并在预算内等待对端关闭。`close` 立即释放连接，适合 `use`、取消和执行作用域清理；重复关闭不重复释放。
- 对端正常关闭返回带状态码与原因的 Closed 事件；连接、TLS、握手、协议和容量错误通过 `WebSocketException` 表达。

客户端不自动重连或重放消息。不提供 WebSocket 服务端。

## 实现与验证

| 职责 | 入口 |
| --- | --- |
| 平台契约 | [websocket](../../cli/compiler/src/main/java/dev/w0fv1/norm/platform/websocket) |
| JDK 传输 | [JdkWebSocketTransport](../../cli/compiler/src/main/java/dev/w0fv1/norm/platform/jdk/JdkWebSocketTransport.java)、[JdkWebSocketConnection](../../cli/compiler/src/main/java/dev/w0fv1/norm/platform/jdk/JdkWebSocketConnection.java) |
| ABI | [stdlib-abi.json](../../cli/compiler/stdlib-abi.json) |
| 真实网络与 TLS 验证 | [JdkWebSocketTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/platform/jdk/JdkWebSocketTest.java) |
| Norm 调用与资源生命周期 | [WebSocketClientTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/stdlib/WebSocketClientTest.java) |
