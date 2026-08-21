# 核心库

核心库应足以编写纯计算程序，并作为系统与应用模块的共同基础。它不依赖 Web 框架。

## 文本

String 是不可变 Unicode 文本值，提供搜索、切分、替换、大小写和显式编码转换。索引单位必须由 API 名称区分 code unit、code point 与 grapheme，不能只暴露含糊的 `length`。

```norm
String message = "point = (${point.x}, ${point.y})"
Bytes encoded = message.encode(encoding: TextEncoding.Utf8)
```

## 数值与时间

Math 提供固定语义的基础函数；Decimal 单独管理十进制 scale 与 rounding。Time 区分 Instant、LocalDate、LocalTime、Duration、TimeZone 和带时区时间，避免把本地时间误当成全球时间点。

## 集合

Array、List、Map 和 Set 全部携带泛型参数。缺失查找结果使用 Option，索引越界使用明确错误。集合赋值的可观察结果彼此独立。

## I/O

Path 只描述路径，File 执行一次性操作，Reader/Writer 与 Stream 管理打开资源。外部资源不会因为 GC 存在就省略 close。

## 数据格式

`Codec<T>` 是序列化共同接口。JSON 等格式通过显式 schema 或可检查的代码生成工作，不把 private 字段布局直接当作线上契约。

## 系统边界

HTTP、SQL、网络与进程 API 分离稳定接口和平台 adapter。超时、取消、事务与错误类型都保留在调用签名或配置中。

进一步阅读：[集合 API](/stdlib/collections-api)、[文件 API](/stdlib/file-api)、[网络 API](/stdlib/network-api)。

