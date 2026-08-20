# 泛型

Norm 泛型参考 Java 的约束与 variance，但保留运行时参数信息。

```norm
class Box&lt;T&gt; { T value }
T max&lt;T extends Comparable<T&gt;>(T a, T b) { ... }
List&lt;? extends Person&gt;
List&lt;? super Employee&gt;
```

禁止 raw type。

## Reified Generics

```norm
List&lt;String&gt;.class
List&lt;User&gt;.class
List&lt;String&gt;.class.T == String.class
```

运行时类型描述保留实际泛型参数，这为反射、JSON、框架和数据库映射提供基础。

