# 应用日志

从 `std.logging` 导入日志函数。宿主通过 `NORM_LOG_LEVEL` 设置默认最低级别，可选 `debug`、`info`、`warn`、`error` 和 `off`。默认级别为 `info`。

控制台时间默认使用机器本地时区。从 `std.system` 导入 `currentSystem` 后，可用 `currentSystem().setTimeZone("Asia/Singapore")` 设置本次运行的时区。例如，新加坡时区显示为 `2026-10-31T19:34:52+08:00`。

`NORM_LOG_OUTPUTS` 接受 JSON 数组。每项的 `name` 使用输出实现的完整类型名，`level` 覆盖默认级别，`source` 匹配指定包及其子包，`options` 将字符串配置传给该实现。

`norm run --debug` 在本次调用中将命令、应用、Norm 包及使用 SLF4J 的 Java 依赖统一设为 debug 阈值。显式命令选项优先于 `NORM_LOG_LEVEL` 和各输出项的 `level`，包括 `off`。输出目的地及 `source` 过滤继续决定事件投递位置。`--` 后的 `--debug` 作为应用参数传入。

应用可实现 `std.logging.LogDefaults` 选择默认输出。宿主设置的 `NORM_LOG_OUTPUTS` 优先。

```json
[
  {"name": "std.logging.ConsoleLogOutputer", "level": "warn"},
  {"name": "std.logging.FileLogOutputer", "source": "shop", "options": {"directory": "log"}}
]
```

[日志函数与路由](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/logging/log.norm) · [文件输出选项](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/logging/file.norm) · [通用实现发现](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/application/application.norm)
