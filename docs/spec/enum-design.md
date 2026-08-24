# Enum 设计

Norm enum 是封闭的代数数据类型。每个 variant 可以不携带数据，也可以拥有独立字段集合。

```norm
enum Token {
    Number(Double value),
    Name(String text),
    End
}
```

## 构造与类型

`Token.Number(value: 1.5)` 的静态类型是 `Token`，不是公开的子类。variant 构造器只初始化其声明的数据，不能继承或被单独实现。泛型 enum 的显式构造在类型名上写全实参，例如 `Result<Integer, Error>.Ok(value: 1)`；省略实参时使用普通调用推断规则。

## 匹配

```norm
String describe(Token token) {
    return switch token {
        case Number(Double value) { break "number" }
        case Name(String text) { break text }
        case End { break "end" }
    }
}
```

编译器递归分析模式覆盖范围，因此每个 switch 都必须穷尽。模式绑定具有静态类型，作用域仅限 case 块。

## 演进规则

在同一兼容性级别内新增 public enum variant 是源码破坏性变更：已有穷尽 switch 会需要新增分支。库作者若需要开放扩展集合，应使用 interface，而不是预留 `Unknown` variant 掩盖模型变化。

`Result<T, E>` 等库类型使用普通 enum 定义，不获得隐藏控制流或特殊运行时表示。
