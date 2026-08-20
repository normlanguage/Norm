# 生态策略

前期不重写整个生态，而采用：

```text
Norm Application
   ↓
Norm Stable APIs
   ↓
Java Compatibility Adapters
   ↓
JDK / JDBC / Maven libraries
```

第一阶段重点：Java interop、Maven dependency resolver、`norm.io`、`norm.time`、`norm.http`、`norm.json`、`norm.sql`、testing、logging。

Java 类型必须通过显式 foreign 边界进入 Norm，避免 Java reference semantics 破坏 Norm value semantics。未来可以设计 `Java&lt;T&gt;` 之类的 foreign object 类型。

随着生态成熟，Java-backed adapter 可逐步替换为 Norm-native 实现，而应用 API 不变。

