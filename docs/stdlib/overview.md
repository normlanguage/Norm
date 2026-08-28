# Standard Library

当前标准库围绕核心值、Unicode 文本、集合、受控系统资源和结构数据建立强类型 API。公开能力以 `norm/stdlib/std` 源码、内建 ABI 与当前版本验收程序为准。

## 已交付模块

| Package | 职责 | 参考 |
| --- | --- | --- |
| `std.core` | Result、Unit、Exception 与核心 interface | [核心类型](https://github.com/w0fv1/Norm/tree/main/norm/stdlib/std/core) |
| `std.annotation` | Annotation 目标、保留与拦截 interface | [Annotation 规范](/spec/annotations) |
| `std.text` | Unicode 规范化与文本构造 | [String](/stdlib/string) |
| `std.collections` | 序列算法与集合 extension | [Collections](/stdlib/collections) |
| `std.math` | Integer 数学函数 | [Math](/stdlib/math) |
| `std.time` | Instant、Duration 与 Clock | [Time](/stdlib/time) |
| `std.io` | Bytes、UTF-8、流与 Resource | [I/O](/stdlib/io) |
| `std.filesystem` | 流式文件读写 | [Filesystem](/stdlib/filesystem) |
| `std.http` | URI、请求、响应与 HTTP client | [HTTP](/stdlib/http) |
| `std.serialization` | 结构映射契约与 metadata | [Serialization](/stdlib/serialization) |
| `std.json` | JSON tree、parse/write 与结构映射 | [JSON](/stdlib/json-api) |
| `std.xml` | XML 结构映射 | [XML](/stdlib/xml-api) |
| `std.yaml` | YAML 结构映射 | [YAML](/stdlib/yaml-api) |
| `std.validation` | 字段与参数约束 | [Validation](/stdlib/validation-api) |
| `std.testing` | 断言值与验收输出协议 | [Testing](/stdlib/testing-api) |

`Array`、`List`、`Map`、`Set`、`Stack`、`Queue`、`Deque`、`Pair`、`Range` 和 `StringBuilder` 是当前内建类型模型的一部分，并由标准库源码提供组合算法。

## 共同规则

- 公共 API 保留完整静态类型，不接受 raw collection；
- 普通缺失使用 `T?`，互斥业务结果使用 enum 或 `Result<T, E>`，系统失败抛出领域 Exception；
- 外部资源通过 `Resource` 与 `use` 确定性关闭；
- 文本 API 明确区分 byte、Unicode code point 和 grapheme；
- 序列化按 Core field ordinal 读取 value，不依赖 JVM reflection 或字符串 getter；
- 每个格式保留自己的领域规则和失败类型，不使用不真实的统一错误模型。

## 当前边界

结构映射只处理 `value`。Class identity、对象图、循环引用和多态尚未进入协议；HTTP server 也尚未交付。完整状态见 [Status](/status)。

具体签名以各页面链接的 Norm 源码为准，文档负责解释模块职责、失败边界和最小用法，不复制第二份完整方法清单。
