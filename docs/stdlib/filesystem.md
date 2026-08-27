# 文件系统

文件系统模块提供 Path、File、Directory 和流操作。Path 是纯值，打开的文件和目录迭代器是必须关闭的外部资源。

相对 Path 以当前 execution platform 的 working directory 为基准。CLI 在启动 execution 时捕获进程工作目录，嵌入方和测试通过平台 adapter 显式注入基准目录。

```norm
Path path = Path(value: "data").resolve(child: "settings.json")
String text = readText(
    path: path,
    encoding: TextEncoding.Utf8
)
```

## 错误

不存在、权限不足、已存在、路径类型错误等系统失败抛出 `FileException`。API 不用 null、状态码或 Result 表示文件操作失败。

## 目录

目录遍历默认不递归，并且不保证平台间相同顺序。递归、是否跟随符号链接、最大深度和错误策略都是显式选项。

## 安全

接受用户路径时，应用需要 normalize 并验证结果仍在允许根目录。字符串前缀比较不足以阻止路径穿越，应使用 Path 的组件级关系检查。

## 写入

写入模式区分 CreateNew、Replace 与 Append。需要原子更新时先写同文件系统临时文件并执行 replace；是否同步落盘由 durability 选项决定。

完整签名见[File API](/stdlib/file-api)。
