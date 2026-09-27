# 控制流表达式

`if`、`for` 和 `switch` 既可以作为语句，也可以作为产生值的表达式。表达式形式始终显式说明值从何处产生。

## If 表达式

```norm
String grade = if score >= 90 {
    break "A"
} else if score >= 60 {
    break "B"
} else {
    break "C"
}
```

每条可达路径都必须使用类型兼容的 `break value`。Norm 不会为缺失分支自动填入 null。

## For 表达式

可迭代的 `for` 依次绑定每个元素。

如果 iterable 只有一个静态可知的元素类型，可以省略绑定类型：

```norm
for index : range(start: 0, end: 10) {
    printLine(index)
}
```

`Range` 推断为 `Integer`。泛型 iterable 从元素类型推断绑定，例如 `List<String>` 的元素是 `String`。只有无法唯一确定静态元素类型时才需要显式绑定类型。

第二个绑定接收从零开始的 `Integer` 索引；值绑定始终在前：

```norm
for value,index : values {
    printLine(index)
    printLine(value)
}
```

条件式 `for` 在每次迭代前重新求值布尔条件：

```norm
for values.size() > 1 && values.last() == 0 {
    values.removeLast()
}
```

`continue` 返回条件检查，`break` 退出循环。

```norm
Integer firstEven = for Integer number : numbers {
    if number % 2 == 0 {
        break number
    }
} else {
    break 0
}
```

循环未执行 `break value` 而正常耗尽时，由 `else` 代码块处理；程序员明确选择这条路径的结果。

## Switch 表达式

```norm
String name = switch direction {
    case North { break "north" }
    case East { break "east" }
    case South { break "south" }
    case West { break "west" }
}
```

封闭 enum 会进行穷尽检查。Variant 可以携带数据，并在 `case` 模式中绑定。

## 为什么使用 `break value`

Norm 有意不让最后一个表达式隐式形成结果。`break value` 为 `if`、`for` 和 `switch` 提供一致的产值规则，并让退出点可见。

下一篇：[保留运行时类型的泛型](/zh/language/generics)。
