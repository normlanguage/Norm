# Enum 声明

enum 声明一个封闭 variant 集合。variant 可以为空，也可以携带类型化数据。

```norm
enum ParseResult {
    Success(int value),
    Empty,
    Invalid(String reason, int position)
}
```

variant 参数采用类型前置，构造时使用命名实参：

```norm
ParseResult result = Invalid(
    reason: "unexpected character",
    position: 3
)
```

## 限制

- variant 名在 enum 的构造命名空间中唯一；
- variant 数据必须满足普通字段的确定赋值规则；
- enum 不能被继承，也不能在其他文件追加 variant；
- generic enum 在 enum 名之后声明类型参数。

```norm
enum Option<T> {
    Some(T value),
    None
}
```

variant 数据只能通过 switch pattern 解构。穷尽性和 `break value` 规则见[Switch](/spec/grammar/switch)。

