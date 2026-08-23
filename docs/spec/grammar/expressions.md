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

## If 表达式

```norm
String state = if active {
    break "active"
} else {
    break "inactive"
}
```

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

所有可能正常完成的路径必须提供兼容值。Norm 不使用块的最后一个表达式作为结果，也不会隐式插入 null。
