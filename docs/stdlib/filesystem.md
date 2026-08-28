# 文件系统

文件系统模块提供路径值、文件字节流和有界文本读取。`Path` 是纯值，`FileReader` 与 `FileWriter` 是必须关闭的外部资源。

相对 Path 以当前 execution platform 的 working directory 为基准。CLI 在启动 execution 时捕获进程工作目录，嵌入方和测试通过平台 adapter 显式注入基准目录。

```norm
Path path = Path(value: "data/settings.json")
String text = readText(
    path: path,
    encoding: TextEncoding.Utf8,
    maximumBytes: 1048576
)
```

## 错误

不存在、权限不足、已存在、路径类型错误等系统失败抛出 `FileException`。API 不用 null、状态码或 Result 表示文件操作失败。

## 写入

`openWrite(path:, mode:)` 的模式区分 `CreateNew`、`Replace` 与 `Append`。`flush()` 推进用户态缓冲，`sync(mode:)` 区分数据同步与数据及 metadata 同步。

完整签名以 [`std.filesystem.files`](https://github.com/w0fv1/Norm/blob/main/norm/stdlib/std/filesystem/files.norm) 为准。
