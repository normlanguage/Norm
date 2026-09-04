# Enum 声明

enum 声明一个封闭 variant 集合。variant 可以为空，也可以携带类型化数据。

```norm
enum ParseResult {
    Success(Integer value),
    Empty,
    Invalid(String reason, Integer position)
}
```

variant 参数采用类型前置，构造时使用命名实参：

```norm
ParseResult result = ParseResult.Invalid(
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
enum Outcome<T, E = String> {
    Success(T value),
    Failure(E error)
}
```

variant 构造是 enum 类型上的调用，沿用普通泛型调用的显式实参与推断规则。以标准库 Result 为例：

```norm
Result<Integer, Error> explicit = Result<Integer, Error>.Ok(value: 1)
Result<Integer> defaultError = Result.Err("invalid")
Result<Integer, Error> inferred = Result.Ok(value: 1)
```

variant 参数支持普通默认值。只有一个必填主参数且其余参数都有默认值时，主参数可以保持单参数调用形式：

```norm
enum Outcome<T> {
    Success(T value, String msg = "")
}

Outcome<Integer> result = Outcome.Success(1)
```

省略 enum 类型实参时，实参和期望类型先参与普通推断，仍未求解但声明了默认类型的尾部参数再采用默认值；其他参数无法确定时必须使用显式形式。variant 数据通过 switch pattern 解构。模式与穷尽规则见[模式匹配](/spec/grammar/patterns)和[Switch](/spec/grammar/switch)。
