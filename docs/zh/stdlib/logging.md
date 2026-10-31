# 应用日志

从 `std.logging` 导入日志函数。宿主通过 `NORM_LOG_LEVEL` 设置默认最低级别，可选 `debug`、`info`、`warn`、`error` 和 `off`。默认级别为 `info`。

控制台时间使用 ISO 8601 格式，末尾的 `Z` 表示 UTC，例如 `2026-10-31T11:34:52Z`。

`NORM_LOG_OUTPUTS` 接受 JSON 数组。每项的 `name` 使用输出实现的完整类型名，`level` 覆盖默认级别，`source` 匹配指定包及其子包，`options` 将字符串配置传给该实现。

`norm run --debug` 在本次调用中将命令、应用、Norm 包及使用 SLF4J 的 Java 依赖统一设为 debug 阈值。显式命令选项优先于 `NORM_LOG_LEVEL` 和各输出项的 `level`，包括 `off`。输出目的地及 `source` 过滤继续决定事件投递位置。`--` 后的 `--debug` 作为应用参数传入。

```json
[
  {"name": "std.logging.ConsoleLogOutputer", "level": "warn"},
  {"name": "std.logging.FileLogOutputer", "source": "shop", "options": {"directory": "logs"}}
]
```

[日志函数与路由](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/logging/log.norm) · [文件输出选项](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/logging/file.norm) · [通用实现发现](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/application/application.norm)
