# I/O 基础 API

`std.io` 保存文件、网络、HTTP 和进程共同使用的字节、编码、读取状态与资源协议。系统操作失败抛出 `IOException` 或更具体的领域异常。

## Bytes

`Bytes` 是取值范围为 0..255 的不可变字节序列。`bytes(values:)` 是公开构造入口；越界元素抛出 `ByteException`，并携带稳定 code、元素位置和值。`slice(start:, length:)` 创建共享底层存储的只读视图。

```norm
Bytes content = bytes(values: [78, 111, 114, 109])
Integer first = content.at(index: 0)
Bytes tail = content.slice(start: 1, length: 3)
```

`toArray()` 返回逻辑独立的数组。完整声明以 [`std.io.bytes`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/io/bytes.norm) 为准。

## 读取与关闭

`ReadChunk.Data(Bytes)` 表示读取到数据，`ReadChunk.Eof` 表示流结束。partial read 与 partial write 是正常结果。`readAll(reader:, maximumBytes:)` 读取有界内容，`writeAll(writer:, content:)` 完成全部写入；无进度、非法进度和超限抛出 `StreamException`。

外部资源实现 `Resource.close()`。`use(resource:, body:)` 在成功和异常路径都关闭资源，并在 body 与 close 同时失败时保留 body 异常为主异常。execution scope 负责清理仍然打开的资源。

## 文本编码

`encodeText(text:, encoding:)` 和 `decodeText(content:, encoding:)` 提供严格 UTF-8 转换。非法输入抛出 `TextException`。

完整声明见 [`std.io.bytes`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/io/bytes.norm)、[`std.io.system`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/io/system.norm) 和 [`std.io.streams`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/io/streams.norm)。
