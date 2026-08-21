# 控制流

Norm 使用 `if`、`for` 和 `switch` 组织分支与循环。这些结构既可以执行语句，也可以显式产生一个值。

## If 语句

```norm
if score >= 60 {
    print("pass")
} else {
    print("fail")
}
```

条件不写括号，代码块必须保留。条件必须是 `bool`。

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

`for` 使用 foreach 形状遍历序列：

```norm
for int number : numbers {
    print("${number}")
}
```

Norm 不提供 C 风格 `for` 或 `while`。数值范围不是特殊语法，而是实现迭代协议的普通值。

当元素类型静态唯一时可以省略循环变量类型。`Range` 推断为 `int`，泛型集合从元素类型参数推断：

```norm
for index : range(start: 0, end: 10) {
    print(index)
}

for name : names {
    print(name)
}
```

上例中若 `names` 是 `List<String>`，`name` 的类型就是 `String`。无法得到唯一静态元素类型时必须显式书写循环变量类型。

## Break 与 Continue

在循环语句中，`continue` 跳到下一次迭代，普通 `break` 结束循环。

```norm
for int number : numbers {
    if number < 0 {
        continue
    }

    if number == 0 {
        break
    }

    print("${number}")
}
```

## For 表达式

`for` 可以查找并产生一个值。`else` 处理正常耗尽且没有执行 `break value` 的路径。

```norm
int firstEven = for int number : numbers {
    if number % 2 == 0 {
        break number
    }
} else {
    break 0
}
```

这段代码没有隐藏的 nullable 结果：调用者明确决定找不到偶数时得到 `0`。如果 `0` 不是合适的语义，应返回一个 enum，例如 `Option<int>`。

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

封闭 enum 的分支必须穷尽。详细模式规则见[Enum 与 Switch](/language/enum-switch)。

下一章：[接口](/language/interfaces)。

