# Network API

网络库提供地址解析、TCP/UDP 与 TLS 的底层能力。HTTP 属于更高层模块，不应泄漏 socket 细节到普通请求 API。

## 地址与解析

```norm
Result<List<IpAddress>, NetworkError> addresses = Dns.resolve(
    host = "example.com"
)

SocketAddress endpoint = SocketAddress(
    address = IpAddress.parse(text = "127.0.0.1"),
    port = 8080
)
```

端口构造检查 `0..65535`。DNS 结果有顺序但不保证唯一；缓存策略由 resolver 实例显式配置。

## TCP

```norm
Result<TcpConnection, NetworkError> opened = Tcp.connect(
    address = endpoint,
    timeout = Duration.seconds(value = 5)
)
```

连接、读取和写入都必须接受超时或取消上下文。一次 read 可以返回少于请求数量的字节；一次 write 也不保证发送全部数据，便利 API 可以提供 `writeAll`。

## TLS

TLS 客户端默认验证证书链和主机名。关闭验证只能通过显式测试配置完成，并应产生明显警告。协议版本、信任根与客户端证书属于 `TlsConfig`，不能由全局可变状态悄悄改变。

socket 是外部资源，所有成功打开的连接都必须确定性关闭。网络错误使用带操作、地址和可重试信息的 enum 表达。

