# I/O 基础 API

`std.io` 保存文件、网络、HTTP 和进程共同使用的字节、编码、读取状态与资源协议。系统操作失败抛出 `IOException` 或更具体的领域异常。

## Bytes

`Bytes` 是取值范围为 0..255 的不可变字节序列。`bytes(values:)` 是公开构造入口；越界元素抛出 `ByteException`，并携带稳定 code、元素位置和值。

```norm
Bytes content = bytes(values: [78, 111, 114, 109])
Integer first = content.at(index: 0)
```

`toArray()` 返回逻辑独立的数组。完整声明以 [`std.io.bytes`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/io/bytes.norm) 为准。

## 读取与关闭

`ReadChunk.Data(Bytes)` 表示读取到非 EOF 数据，`ReadChunk.Eof` 表示流结束；EOF 不使用空 Bytes、null 或异常表达。partial read 是正常结果，调用方需要完整内容时由后续 `readAll` 或 `writeAll` 组合。

外部资源实现 `Resource.close()`。显式关闭与 execution scope 清理共享同一个底层关闭状态，重复关闭不会重复操作宿主资源，也不会掩盖第一次关闭失败。

`TextEncoding` 和 `IOException` 定义在 [`std.io.system`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/io/system.norm)，`ReadChunk` 与 `Resource` 定义在 [`std.io.streams`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/io/streams.norm)。
