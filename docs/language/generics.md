# 泛型

Norm 泛型参考 Java 的约束与 variance，但保留运行时参数信息。

```norm
class Box<T> { T value }
T max<T extends Comparable<T>>(T a, T b) { ... }
List<? extends Person>
List<? super Employee>
```

禁止 raw type。

## Reified Generics

```norm
List<String>.class
List<User>.class
List<String>.class.T == String.class
```

运行时类型描述保留实际泛型参数，这为反射、JSON、框架和数据库映射提供基础。
