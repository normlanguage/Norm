# 08 Lambda 与 Extension

函数可以作为值传递；extension 只改变调用形式，不改变目标类型或动态分派。

<<< ../../norm/tests/docs/tour/08_lambdas_extensions.norm{norm}

输出：

```text
12
Norm
```

## 函数值与 Lambda

完整函数类型写作 `Function<返回类型(参数类型...)>`。参数位置的 callable 声明是同一类型的简写。Lambda 可以从期望函数类型获得参数和结果类型，也可以显式声明参数类型。

Lambda 的最后一个表达式形成结果；具名函数仍必须显式 `return`。Lambda 可以捕获外层局部、参数和 `this`，被捕获绑定必须 effectively final。

绑定方法引用使用 `receiver.method`，普通函数可以直接赋给兼容的函数类型。需要不绑定 receiver 的声明引用时使用 `Owner.method.function`。

## Extension function

Extension 的第一个参数是接收者，成员式调用时不再出现在实参列表中。它必须显式导入并静态解析；真实实例方法按名称优先。普通函数不会因为首参数类型相同而自动成为 extension。

完整规则见[函数高级规则](/spec/grammar/functions-advanced)和[Extension 语法](/spec/grammar/functions#extension-function)。

上一章：[集合与迭代](/learn/collections)。下一章：[错误与异常](/learn/errors)。
