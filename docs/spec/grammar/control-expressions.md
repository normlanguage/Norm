# 控制流表达式

`if`、`for` 和 `switch` 在需要值的上下文中都是表达式。Norm 使用 `break value` 标记表达式结果，不采用“代码块最后一项自动返回”的规则。

```norm
String state = if ready {
    break "ready"
} else {
    break "waiting"
}
```

## 完成规则

控制结构有三种完成方式：

- 正常完成：作为语句使用，不产生值；
- `break value`：结束当前控制表达式并产生值；
- `return`、`throw`：离开当前函数或抛出异常，不要求再产生局部结果。

当结构作为表达式使用时，每条可能正常完成的路径都必须执行 `break value`。不同路径的结果必须能合并为唯一静态类型。

```norm
Integer value = if condition {
    break 1
} else {
    throw InvalidState()
}
```

上例合法，因为 `throw` 路径不会正常完成。

## 嵌套目标

`break value` 作用于最近一层正在求值的控制表达式。为避免目标不清晰，当前语法不提供带标签的 break；复杂嵌套应提取为函数。

