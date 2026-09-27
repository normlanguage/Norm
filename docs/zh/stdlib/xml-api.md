# XML API

`std.xml` 实现格式无关的序列化接口，并提供 XML 便捷入口。公开签名以 [`xml.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/xml/xml.norm) 为准。

```norm
@Serializable()
@SerialName(name: "user")
value User {
  @XmlAttribute()
  Integer id
  @SerialName(name: "display_name")
  String name
}

String encoded = User(id: 7, name: "Norm").toXml()
User decoded = encoded.fromXml<User>()
```

字段默认映射为子元素，Array/List 使用 `item` 子元素，Map 使用 `entry`、`key` 与 `value`。`@XmlAttribute` 可把标量或 enum 字段映射为属性；nullable 字段缺失时解码为 `null`。根元素名与字段名复用 `@SerialName`。

解析严格拒绝未知、重复、缺失字段、错误根元素、数值越界、DTD 与外部实体。失败抛出 `XmlException`，携带稳定的 `code`、`path`、`offset`、`line` 与 `column`。
