# 05 数据 Enum 与 Switch

Enum 定义封闭的状态集合；`switch` 解构状态并由编译器检查覆盖范围。

<<< ../../norm/tests/docs/tour/05_enum_switch.norm{norm}

输出：

```text
N-42
```

variant 可以不携带数据，也可以声明一个或多个有类型的 payload。构造器位于 enum 命名空间，并使用参数标签。

## Switch 是表达式

表达式分支使用 `break value` 产生结果。Norm 不把分支最后一个表达式隐式作为 switch 结果。

每个 switch 都必须穷尽：

- 封闭 enum 覆盖全部 variant；
- nullable 类型额外覆盖 `null`；
- 开放类型或无限值域使用 `_` 覆盖剩余值；
- 已被前序模式完全覆盖的 case 不可达。

variant payload 内可以继续使用 variant、类型化绑定、`_`、兼容字面量或 `null`，因此模式能够递归嵌套。被匹配表达式只求值一次，首个匹配 case 独占执行，没有 fallthrough。

完整规则见[模式匹配](/spec/grammar/patterns)与[Switch 参考](/spec/grammar/switch)。

上一章：[Class、Value 与 Interface](/learn/data-model)。下一章：[Null 与类型推断](/learn/nullability-inference)。
