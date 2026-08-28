# 03 函数与调用

函数是顶层语言结构。返回类型、参数类型和公开参数名共同形成可见的调用契约。

<<< ../../norm/tests/docs/tour/03_functions.norm{norm}

输出：

```text
40
```

## 声明

```text
返回类型 函数名(参数类型 参数名, ...) { ... }
```

有结果的具名函数使用 `return value`。顶层函数省略返回类型时固定为 `Void`，不会根据函数体推断返回类型。

## 参数标签

多参数调用写出参数名：

```norm
Integer difference = subtract(left: 140, right: 100)
```

标签选择形参槽位，但实参表达式仍按源码从左到右求值。单参数调用可以省略标签；多参数调用中，与形参同名的裸标识符可以缩写。具名实参与位置实参不能混用。

## 方法

实例方法可以访问字段。class 方法省略返回类型时是 fluent 方法，正常完成或裸 `return` 都返回 `this`；真正无结果的方法显式写 `Void`。

重载、覆盖和完整 callable 语法见[函数参考](/spec/grammar/functions)与[函数高级规则](/spec/grammar/functions-advanced)。

上一章：[值与绑定](/learn/bindings)。下一章：[Class、Value 与 Interface](/learn/data-model)。
