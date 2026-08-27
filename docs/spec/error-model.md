# 错误模型

Norm 区分正常契约内的失败值与打断正常求值的 Exception。

## Result

`std.core.Result<T, E>` 是标准库定义的普通泛型 enum，用于应用领域中由函数契约显式返回的互斥结果。它通过 switch 显式处理，语言不提供隐藏的自动传播运算符。

```norm
String message = switch reserve(command: command) {
    case Ok(Reservation value) { break value.id }
    case Err(Rejection reason) { break reason.message }
}
```

## 普通缺失

查找缺失且不需要错误细节时使用 nullable 返回类型。Result 是否属于契约由应用领域决定，不能用它绕开系统层异常边界。

## Exception

`std.core.Exception` 是所有可抛出异常的名义 class 根类型。异常子类使用普通单继承，catch 只接受非 nullable、非泛型的 Exception 子类型。`throw`、`try`、`catch` 与 `finally` 处理无法作为当前函数正常结果继续的异常。Norm 不使用 checked exception，也不会把 Result.Err 自动转换为 Exception。

std.io、filesystem、network、http、time、process、regex、crypto 与 concurrent 的操作失败统一抛出领域 Exception，不返回 Result。具体边界以[标准库 API 原则](/stdlib/library-design-principles)为准。

运行时参数错误、越界、除零等工具链故障使用稳定运行时错误码，不属于用户 Exception 层级，也不会被 catch 截获。

## 边界转换

传输层可以把领域 Result 映射为 HTTP 状态，CLI 可以映射为退出码。映射发生在边界函数中，核心业务类型不依赖传输协议。

finally 中产生的 return、throw、break 或 continue 会替代原完成结果；库应尽量通过作用域资源 API 降低清理失败的歧义。
