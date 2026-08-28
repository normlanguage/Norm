# Serialization

`std.serialization` 定义格式无关的映射契约与公共 metadata。公开签名以 [`core.norm`](https://github.com/w0fv1/norm/blob/main/norm/stdlib/std/serialization/core.norm) 为准。

```norm
@Serializable()
value Message {
  @SerialName(name: "message_text")
  String text
}

Void roundTrip(DataMapper mapper) {
  DataWriter<Message> writer = mapper.writer<Message>()
  DataReader<Message> reader = mapper.reader<Message>()
  Message decoded = reader.readString(source: writer.writeString(value: Message(text: "Norm")))
}
```

`DataMapper`、`DataReader<T>` 与 `DataWriter<T>` 是应用层唯一的格式抽象；JSON、XML 与 YAML 各自实现它们。`@Serializable`、`@SerialName`、`@SerialIgnore` 描述共享结构，格式专属 metadata 留在对应格式包中。

运行时只为精确 `CoreType` 编译并缓存 reader/writer plan。自动结构映射只处理 value；class identity、对象图、循环引用与多态需要独立协议。失败抛出对应格式的类型化异常。

格式入口见 [JSON API](/stdlib/json-api)、[XML API](/stdlib/xml-api) 与 [YAML API](/stdlib/yaml-api)，内部边界见[序列化运行时](/design/serialization-runtime)。
