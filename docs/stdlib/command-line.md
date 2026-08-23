# Command Line

命令行库把原始参数解析为明确的 option、flag、position argument 和 subcommand，不通过 annotation 从 class 字段推导接口。

```norm
CommandSpec serve = CommandSpec(name: "serve")
    .option(name: "port", parser: Parsers.integer(), required: true)
    .flag(name: "verbose", shortName: "v")

ParseResult arguments = serve.parse(values: Process.arguments())
```

## 解析规则

长 option 使用 `--name value` 或明确允许的 `--name=value`。短 flag 可以组合，但带值的短 option 不参与组合。`--` 结束 option 解析，后续值全部作为位置参数。

缺失必填项、未知 option、无效值和互斥冲突形成结构化 `CliError`。库根据 CommandSpec 生成 usage，错误消息和退出码由应用决定。

## 子命令

子命令拥有自己的参数空间和帮助文本。全局 option 必须在 spec 中明确声明可出现在子命令前后，parser 不猜测。

## 敏感参数

密码和 token 不建议通过命令行传递，因为它们可能出现在进程列表和 shell history。Secret option 在帮助和诊断中必须遮蔽，但环境或标准输入仍是更合适的来源。
