# 表达式语法

## 基本形式

```text
Expression := Literal
            | Identifier
            | MemberAccess
            | Call
            | Index
            | UnaryExpression
            | BinaryExpression
            | IfExpression
            | ForExpression
            | SwitchExpression
```

## 成员、调用与索引

```norm
Point point = Point(x: 2, y: 4)
Integer x = point.x
String first = names[0]
```

多参数调用使用 `name: value`。单参数可以省略名称；多参数中的裸标识符与对应参数同名时也可以省略标签。其他位置实参非法，具名实参可以按照任意顺序书写。接收者、实参和索引按源码顺序求值一次。安全导航、隐式 await 和动态成员查找不属于当前语法。

## 运算

```norm
Integer total = base + quantity * price
Boolean accepted = ready && total > 0
```

运算符固定且不能重载。逻辑运算只接受 Boolean 并短路。赋值单独作为语句，不产生可用于更大表达式的值。

`throw` 可以出现在需要值的表达式位置，操作数必须是非空 `Exception`。它不产生值，而是沿用普通抛出语句的异常传播与 `catch/finally` 规则；结果类型由所在位置的期望类型决定。

```norm
var todo = repository.findById(id) ?? throw Exception(message: "任务已不存在")
```

`??` 只在左侧为 null 时求值右侧，因此上例成功查询后 `todo` 为非空类型，不会创建异常；查询表达式只执行一次。

## If 表达式

```norm
String state = if active {
    "active"
} else {
    "inactive"
}
```

条件必须为 Boolean；分支使用末尾表达式或显式 `break value` 提供结果。正常完成的路径必须提供值；分支中的 `return` 仍退出外围函数。分支保留普通 if 的类型收窄与局部作用域。

只包含一个结果表达式的分支可以省略大括号：`if editing "" else task.title`。这与换行位置无关；包含声明、赋值或多个语句的分支仍使用大括号。`else` 归属于最近一个尚未配对的 `if`。

条件使用括号时，右括号明确结束条件，例如 `if (ready) -1 else 0`。不使用条件括号时，条件按普通表达式解析；分支以负号、括号等可能继续条件的符号开头时，应为整个条件加括号或保留分支大括号。

## For 表达式

```norm
Integer found = for Integer number : numbers {
    if number > 0 { break number }
} else {
    break 0
}
```

## Switch 表达式

```norm
String text = switch token {
    case Name(String value) { break value }
    case End { break "end" }
}
```

控制流表达式所有可能正常完成的路径必须提供兼容值，不会隐式插入 null。可调用体的末尾返回规则见[函数高级规则](functions-advanced.md)。
