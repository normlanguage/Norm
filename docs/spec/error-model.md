# 错误模型

Norm 区分正常契约内的失败值与打断正常求值的 Exception。

## Result

```norm
enum Result<T, E> {
    Ok(T value),
    Err(E error)
}
```

解析失败、资源不存在、远程拒绝等调用者预计会处理的结果使用 Result。它是普通 enum，通过 switch 显式处理。语言不提供隐藏的自动传播运算符。

```norm
String message = switch parse(text: input) {
    case Ok(Integer value) { break "${value}" }
    case Err(ParseError error) { break error.message }
}
```

## 普通缺失

查找缺失且不需要错误细节时使用 nullable 返回类型。需要区分多种失败原因时使用 Result。

## Exception

`throw`、`try`、`catch` 与 `finally` 处理无法作为当前函数正常结果继续的异常。Norm 不使用 checked exception，也不会把 Result.Err 自动转换为 Exception。

## 边界转换

传输层可以把领域 Result 映射为 HTTP 状态，CLI 可以映射为退出码。映射发生在边界函数中，核心业务类型不依赖传输协议。

finally 中抛出的新异常会替代原完成结果；库应尽量通过作用域资源 API 降低清理失败的歧义。
