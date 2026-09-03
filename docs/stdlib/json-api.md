# JSON API

`std.json` 实现格式无关的序列化接口，并提供需要动态 JSON 时使用的 `JsonValue` tree。公开签名以 [`json.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/json/json.norm) 为准；公共 Annotation 与 mapper 接口见 [Serialization](/stdlib/serialization)。

```norm
@Serializable()
value User {
  @SerialName(name: "user_name")
  String name
}

String encoded = User(name: "Norm").toJson()
User decoded = encoded.fromJson<User>()
```

自动结构序列化只接受显式标记的 `value`。`@SerialName` 重命名字段；`@SerialIgnore` 只允许用于 nullable 字段，解码后写入 `null`。嵌套 value、nullable、Array/List、`Map<String, T>`、无 payload enum 和基础标量递归使用同一精确类型 shape。

`fromJson<T>` 严格拒绝未知字段、重复字段、缺少的非 nullable 字段、数值溢出和尾随内容。解析、shape 与资源限制失败统一抛出 `JsonException`，其 `code`、`path`、`offset`、`line` 和 `column` 可用于定位数据问题。目标 value 通过规范构造器创建，因此字段 interceptor 与 validation 约束不会被绕过。

HTTP 组合见 [HTTP API](/stdlib/http)，运行时边界见[序列化运行时](/design/serialization-runtime)。
