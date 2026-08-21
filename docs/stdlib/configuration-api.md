# Configuration API

配置库把不同来源的数据读取为明确、可验证的值。它不依赖字段 annotation 自动注入，也不会静默猜测类型。

## 配置来源

```norm
Config config = Config.empty()
    .withSource(source: EnvSource(prefix: "NORM_"))
    .withSource(source: FileSource(path: Path("app.toml")))
```

后加入的来源覆盖先前来源。来源顺序必须在调用点可见；进程环境、文件与命令行不会被隐式合并。

## 类型化读取

```norm
Result<String, ConfigError> host = config.string(key: "server.host")
Result<int, ConfigError> port = config.int(key: "server.port")
Result<bool, ConfigError> debug = config.bool(key: "server.debug")
```

缺失键、格式错误和越界值分别使用 `MissingKey`、`InvalidValue` 与 `OutOfRange` 表达。默认值由调用者显式提供：

```norm
int port = switch config.int(key: "server.port") {
    case Ok(int value) { break value }
    case Err(MissingKey) { break 8080 }
    case Err(ConfigError error) { throw InvalidConfiguration(error: error) }
}
```

## 解码结构

结构化解码通过显式 `ConfigDecoder<T>` 完成。运行时反射可以辅助实现 decoder，但库不能仅凭字段名称改变公开配置契约。敏感值在日志和错误消息中必须被遮蔽。

