# WebSocket

WebSocket handler 在 HTTP upgrade 成功后获得双向连接。认证在 upgrade 前完成，连接上下文保存稳定 Principal 和 trace 信息。

```norm
WebSocketHandler chat = WebSocketHandler(
    onOpen = openChat,
    onMessage = receiveChat,
    onClose = closeChat
)

router.webSocket(path = "/chat", handler = chat)
```

## 消息

API 区分 TextMessage、BinaryMessage、Ping、Pong 和 Close。分片帧由协议层重组，但总消息大小有上限。文本必须是有效 UTF-8。

## 背压

send 返回可等待结果；发送队列有容量限制。消费者过慢时策略选择等待、丢弃非关键消息或关闭连接，不能无限增长内存。

## 生命周期

应用关闭时先停止接受 upgrade，再发送正常关闭帧并等待有限时间。网络中断可能没有 close handshake，业务在线状态必须依赖租约或超时。

多实例广播需要外部 pub/sub。进程内连接对象不能放进普通分布式 session，也不能跨节点共享。

