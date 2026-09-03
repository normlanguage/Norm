# Configuration

`std.configuration` 将类型化 Norm value 映射为框架可消费的扁平属性。公开签名以 [`configuration.norm`](https://github.com/w0fv1/Norm/blob/main/norm/stdlib/std/configuration/configuration.norm) 为准。

```norm
@Serializable()
value Server {
  String contextPath
  Integer port
}

MutableMap<String?, Any?> properties = configurationProperties(
  value: Server(contextPath: "/api", port: 8080)
)
```

映射结果包含 `context-path=/api` 和 `port=8080`。普通字段由 camelCase 转为 kebab-case；`@SerialName` 提供显式外部名称；nullable 的 null 值不产生属性；List 使用 `[index]` 路径；`Map<String, T>` 使用 map key 作为路径段。

`@ConfigurationKey` 将命名集合元素中的一个 String 字段用作路径段，并从属性值中排除该字段。`@ConfigurationValue` 将只承载一个配置标量的 value 解包到当前位置。这两个 Annotation 只描述通用配置结构，不包含 Micronaut、Spring 或其他 Java 框架语义。

配置映射与 JSON、XML、YAML 共用 `@Serializable`、`@SerialName`、`@SerialIgnore`、Core field ordinal 和缓存后的结构 shape。运行时直接生成宿主 `LinkedHashMap`，不经过文本格式，不使用 JVM reflection。
