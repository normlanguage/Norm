# Configuration

`std.configuration` maps typed Norm values to flat properties consumable by frameworks. [`configuration.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/configuration/configuration.norm) defines its public signatures.

```norm
@Serializable()
value Server {
  String contextPath
  Integer port
}

JavaMap<String, Any?> properties = configurationProperties(
  value: Server(contextPath: "/api", port: 8080)
)
```

The result contains `context-path=/api` and `port=8080`. Ordinary fields convert from camelCase to kebab-case; `@SerialName` provides an explicit external name. A nullable `null` produces no property. Lists use `[index]` paths, and `Map<String, T>` uses map keys as path segments.

`@ConfigurationKey` uses one String field of a named collection element as a path segment and excludes that field from property values. `@ConfigurationValue` unwraps a value containing only one configuration scalar at the current path. These annotations describe generic configuration structure, without Micronaut, Spring, or other Java framework semantics.

Configuration mapping shares `@Serializable`, `@SerialName`, `@SerialIgnore`, Core field ordinals, and cached structural shapes with JSON, XML, and YAML. The runtime creates a host `LinkedHashMap` directly, without passing through a text format or using JVM reflection.
