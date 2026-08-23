# Process

Process 模块描述当前进程和子进程生命周期。启动命令的完整 API 见[Process API](/stdlib/process-api)。

## 当前进程

```norm
List<String> arguments = Process.arguments()
ProcessId id = Process.id()
Instant startedAt = Process.startedAt()
```

arguments 不包含运行时自身消费的内部参数。工作目录和环境通过 Environment 模块读取。

## 退出

`main` 正常返回表示退出码 0；返回 `Integer` 的 main 可以显式选择退出码。`Process.exit(code)` 立即结束进程，不运行普通栈清理，因此只用于无法继续的顶层边界。

## 信号与关闭

```norm
Process.onShutdown(handler: gracefulShutdown)
```

关闭 handler 按注册逆序运行并受总超时限制。handler 应停止接收新工作、完成有限清理并返回，不能假设所有信号都可被捕获。

进程级 API 不能用于隐藏全局服务定位。应用依赖应由 main 显式构造并传入。

