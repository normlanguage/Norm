# 控制流

Norm 使用 `if`、`for` 和 `switch` 组织分支与循环。这些结构既可以执行语句，也可以显式产生一个值。

## If 语句

```norm
if score >= 60 {
    printLine("pass")
} else {
    printLine("fail")
}
```

条件不写括号，代码块必须保留。条件必须是 `Boolean`。

## If 表达式

当 `if` 出现在需要值的位置时，每条可达路径都必须通过 `break value` 提供兼容类型的结果。

```norm
String grade = if score >= 90 {
    break "A"
} else if score >= 60 {
    break "B"
} else {
    break "C"
}
```

Norm 不使用代码块的最后一个表达式作为结果。`break` 让值从哪里产生保持可见。

下面的表达式不完整，因此编译失败：

```norm
String label = if enabled {
    break "enabled"
}
```

Norm 不会为缺失分支隐式补上 null。

## For 循环

遍历式 `for` 通过 `std.core.Iterable<T>` 依次绑定元素：

```norm
for Integer number : numbers {
    printLine(number)
}
```

条件式 `for` 在每轮开始前重新计算 Boolean 条件：

```norm
for values.size() > 1 && values.last() == 0 {
    values.removeLast()
}
```

条件初始为 false 时循环执行零次。`continue` 重新进入条件检查，`break` 结束循环。数值范围不是特殊语法，而是显式实现 `Iterable<Integer>` 的普通值。

当元素类型静态唯一时可以省略循环变量类型。`Range` 推断为 `Integer`，泛型集合从元素类型参数推断：

```norm
for index : range(start: 0, end: 10) {
    printLine(index)
}

for name : names {
    printLine(name)
}
```

上例中若 `names` 是 `List<String>`，`name` 的类型就是 `String`。无法得到唯一静态元素类型时必须显式书写循环变量类型。

## Break 与 Continue

在循环语句中，`continue` 跳到下一次迭代，普通 `break` 结束循环。

```norm
for Integer number : numbers {
    if number < 0 {
        continue
    }

    if number == 0 {
        break
    }

    printLine(number)
}
```

## For 表达式

`for` 可以查找并产生一个值。`else` 处理正常耗尽且没有执行 `break value` 的路径。

```norm
Integer firstEven = for Integer number : numbers {
    if number % 2 == 0 {
        break number
    }
} else {
    break 0
}
```

调用者为正常耗尽路径明确选择结果。需要表达缺失时可以返回 nullable 类型。

## Switch

`switch` 用于常量、类型和 enum variant 的匹配。它作为表达式使用时同样要求所有路径显式产生值。

```norm
String name = switch direction {
    case North { break "north" }
    case East { break "east" }
    case South { break "south" }
    case West { break "west" }
}
```

每个 switch 都必须穷尽。被匹配表达式只求值一次，首个匹配 case 独占执行且没有 fallthrough；表达式 case 的正常完成路径必须执行 `break value`。详细模式规则见[Enum 与 Switch](/language/enum-switch)。

下一章：[接口](/language/interfaces)。
