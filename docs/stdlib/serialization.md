# Serialization

序列化把类型化值转换为外部数据格式。公开格式是长期契约，不能简单等同于当前字段布局。

```norm
interface Codec<T> {
    Result<Bytes, EncodeError> encode(T value)
    Result<T, DecodeError> decode(Bytes input)
}
```

## Schema

JSON、二进制或消息格式分别提供 Codec 实现。应用可以通过显式 builder 或代码生成定义字段名称、版本、缺失默认值和未知字段策略。

```norm
JsonCodec<Point> pointCodec = JsonCodec<Point>.builder()
    .field(name = "x", read = Point.x)
    .field(name = "y", read = Point.y)
    .construct(factory = Point)
    .build()
```

反射可以减少样板，但必须生成可检查 schema；不能让重命名 private 字段静默改变线上格式。

## 安全

decoder 对深度、集合长度、字符串和总字节数设置上限。输入不会指定任意运行时 class；多态解码只接受注册的 enum 或类型表。

Ref identity 默认不序列化。对象图需要共享或循环时必须使用专门协议，避免普通 JSON codec 隐藏 identity 语义。

