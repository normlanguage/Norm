# Ecosystem Strategy

The initial approach uses the existing ecosystem instead of rewriting it all:

```text
Norm Application
   ↓
Norm Stable APIs
   ↓
Java Compatibility Adapters
   ↓
JDK / JDBC / Maven libraries
```

The first phase focuses on Java interop, a Maven dependency resolver, `norm.io`, `norm.time`, `norm.http`, `norm.json`, `norm.sql`, testing, and logging.

Java types must cross an explicit foreign boundary into Norm so Java reference semantics do not break Norm value semantics. A foreign object type such as `Java&lt;T&gt;` may be designed later.

As the ecosystem matures, Java-backed adapters can gradually be replaced with Norm-native implementations without changing application APIs.
