# Process API

进程 API 使用参数列表启动程序，不要求调用者拼接 shell 命令。默认情况下不会经过 shell 展开变量、通配符或重定向符号。

```norm
Command command = Command(
    program: "normc",
    arguments: ["check", "src"]
)

ProcessOutput result = command.run(
    timeout: duration(seconds: 30, nanoseconds: 0)
)
```

## 输入与输出

`ProcessOutput` 包含退出状态、标准输出和标准错误。文本解码需要显式 encoding；二进制输出以 `Bytes` 保留。输出大小必须支持上限，避免子进程耗尽内存。

长时间任务使用 `Process`：

```norm
Process started = command.start()
```

调用者负责读取管道、等待退出并在取消时终止进程。`terminate()` 请求正常结束，`kill()` 表示强制结束，两者不能混为一个布尔参数。

启动失败、超时、取消和管道 I/O 失败抛出 `ProcessException`。子进程非零退出码属于 `ProcessOutput`，不自动转成异常。

## 环境与目录

工作目录和环境变量由 Command 值显式设置。环境继承策略必须选择 `inherit` 或 `empty`，敏感变量不会自动写入日志。

需要 shell 语法时使用单独的 `ShellCommand` API，并把注入风险保留在类型和调用点上。
