# YAML API

`std.yaml` 实现格式无关的序列化接口。公开签名以 [`yaml.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/yaml/yaml.norm) 为准；共享 mapper 与 Annotation 见 [Serialization](/stdlib/serialization)。

```norm
@Serializable()
value User {
  @SerialName(name: "user_name")
  String name
}

String encoded = User(name: "Norm").toYaml()
User decoded = encoded.fromYaml<User>()
```

结构映射支持 nullable、Array/List、`Map<String, T>`、无 payload enum、嵌套 value 与共享的 `@SerialName`、`@SerialIgnore`。输出使用稳定的 block YAML，不写 document start marker，并为可能产生隐式类型歧义的字符串保留引号。

解码只接受单文档和字符串 mapping key，严格拒绝未知、重复、缺失字段、数值越界、alias 与显式 tag。失败抛出 `YamlException`，携带稳定的 `code`、`path`、`offset`、`line` 与 `column`。
