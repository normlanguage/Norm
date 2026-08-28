# File API

文件 API 区分路径值、一次性便利操作和需要显式关闭的流。内存由运行时管理，但文件描述符属于外部资源。

## Path

```norm
Path root = Path(value: "data")
Path file = Path(value: "data/users.json")
```

`Path` 保存平台路径文本。相对路径由 execution platform 的 working directory 解析。

## 有界文本读取

```norm
String text = readText(
    path: file,
    encoding: TextEncoding.Utf8,
    maximumBytes: 1048576
)
```

不存在、权限不足、已存在等失败抛出 `FileException`。API 不用 null、状态码或 Result 表示文件操作失败。

## 流与关闭

大文件使用 `FileReader` 与 `FileWriter`。`read` 返回 `ReadChunk`，`write` 返回本次写入的字节数。

```norm
FileReader reader = openRead(path: file)
Bytes content = use<Bytes>(resource: reader, body: () {
    readAll(reader: reader, maximumBytes: 1048576)
})

FileWriter writer = openWrite(path: file, mode: FileWriteMode.Replace)
use<Integer>(resource: writer, body: () {
    writeAll(writer: writer, content: content)
    writer.sync(mode: FileSyncMode.DataAndMetadata)
    0
})
```

完整声明以 [`std.filesystem.files`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/filesystem/files.norm) 为准。
