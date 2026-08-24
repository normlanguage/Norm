# File API

文件 API 区分路径值、一次性便利操作和需要显式关闭的流。内存由运行时管理，但文件描述符属于外部资源。

## Path

```norm
Path root = Path("data")
Path file = root.resolve(child: "users.json")
Path normalized = file.normalize()
```

Path 只表示平台路径，不保证目标存在。拼接不会把未经验证的用户输入自动视为安全子路径；服务端代码必须在 normalize 后检查结果仍位于允许根目录内。

## 一次性读写

```norm
Result<String, FileError> text = File.readText(
    path: file,
    encoding: TextEncoding.Utf8
)

Result<Unit, FileError> saved = File.writeText(
    path: file,
    text: content,
    mode: WriteMode.Replace
)
```

预期的不存在、权限不足、已存在等失败通过 `FileError` variant 返回。API 不用 null 表示文件缺失。

## 流与关闭

大文件使用 `FileReader`、`FileWriter` 或字节流。打开成功后，调用者必须通过作用域资源 API或 `try/finally` 关闭资源。重复 close 应安全但不应掩盖第一次关闭错误。

原子替换、同步落盘和符号链接跟随策略必须是显式选项；默认值不能让安全敏感行为依赖操作系统差异。
