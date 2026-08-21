# UUID API

`Uuid` 是一个 128 位不可变 value，用于解析、比较、格式化和生成标准 UUID。它不是 String 的别名。

```norm
Result<Uuid, UuidError> parsed = Uuid.parse(
    text: "550e8400-e29b-41d4-a716-446655440000"
)

Uuid id = Uuid.random()
String text = id.toString()
```

## 解析与格式

默认解析接受规范的带连字符十六进制格式，大小写均可；输出统一使用小写。宽松格式必须由单独方法请求，不能让默认 parser 接受含糊输入。

## 生成

- `Uuid.random()` 使用安全随机源生成随机 UUID；
- 时间有序 UUID 使用单独的 `Uuid.timeOrdered(clock, random)` API；
- 名称型 UUID 要求显式 namespace 和哈希版本。

生成函数不得回退到普通伪随机数。系统随机源不可用时返回或抛出明确错误，而不是降低质量。

## 相等与排序

相等比较全部 128 位。排序按无符号字节的稳定字典序定义，仅用于索引和确定性输出，不代表所有 UUID 版本的创建时间顺序。

数据库和序列化适配器应保留 Uuid 类型信息；跨边界时可以使用 16 字节表示或规范字符串表示。

