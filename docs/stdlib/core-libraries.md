# 核心库

核心库应足以编写纯计算程序，并作为系统与应用模块的共同基础。它不依赖 Web 框架。

## 文本

String 是不可变 Unicode 文本值，提供搜索、切分、替换、大小写和显式编码转换。文本使用 `byteSize()`、`codePointSize()` 与 `graphemeSize()` 区分单位，不提供含糊的 `size()` 或 `length`。

```norm
String message = "Hello, " + name
Bytes encoded = encodeText(text: message, encoding: TextEncoding.Utf8)
```

## 数值与时间

Math 提供固定语义的基础函数。计划中的 Decimal 将单独管理十进制 scale 与 rounding；时间库区分 Instant、Duration 与 Clock，后续日历类型不会把本地时间误当成全球时间点。

## 集合

Array、List、Map 和 Set 全部携带泛型参数。缺失查找结果使用 nullable，索引越界使用明确错误。集合赋值的可观察结果彼此独立。

## I/O

Path 只描述路径，File 执行一次性操作，Reader/Writer 与 Stream 管理打开资源。外部资源不会因为 GC 存在就省略 close。

## 数据格式

结构序列化由 runtime Annotation 显式加入契约，格式 mapper 复用 Core metadata 与缓存的字段访问计划。公共接口见 [Serialization](/stdlib/serialization)，格式入口见 [JSON API](/stdlib/json-api)、[XML API](/stdlib/xml-api) 与 [YAML API](/stdlib/yaml-api)，架构见[序列化运行时](/design/serialization-runtime)。

## 系统边界

HTTP、SQL、网络与进程 API 分离稳定接口和平台 adapter。超时、取消、事务与错误类型都保留在调用签名或配置中。

进一步阅读：[集合 API](/stdlib/collections-api)、[文件 API](/stdlib/file-api)、[网络 API](/stdlib/network-api)、[HTTP API](/stdlib/http)、[Serialization](/stdlib/serialization)。
