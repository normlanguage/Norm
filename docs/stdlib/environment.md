# Environment

Environment 提供进程环境变量、工作目录和平台信息。读取环境是外部输入，缺失与空字符串必须区分。

```norm
Option<String> mode = Environment.get(name: "APP_MODE")
Path current = Environment.currentDirectory()
Platform platform = Environment.platform()
```

## 快照

```norm
Map<String, String> variables = Environment.snapshot()
```

snapshot 返回调用时的独立值，之后的修改不会影响进程环境。直接修改环境变量是全局副作用且线程行为因平台而异，标准库不把它作为普通应用配置方式。

## 名称规则

环境变量名称的大小写敏感性随平台不同。跨平台应用应使用规范 ASCII 大写名称，并避免依赖枚举顺序。值按平台字节解码失败时返回明确错误，而不是静默替换字符。

## 测试

需要环境配置的函数应接受 Config 或显式 Map，而不是在任意位置直接读取 Environment。测试可以构造输入值，无需修改真实进程环境。

平台检测只描述 OS、architecture 和运行时能力；业务逻辑不应通过模糊的“生产环境”全局开关改变语义。

