# 循环语法

Norm 使用 `for` 表达遍历循环和条件循环。遍历式 `for` 只接受显式实现标准库 `Iterable<T>` interface 的值，并通过其 `Iterator<T>` 迭代。

```norm
for String name : names {
    printLine(name)
}
```

## 语法形状

```text
For := ForEach | ConditionalFor
ForEach := "for" Type? Identifier ("," Identifier)? ":" Expression Block ("else" Block)?
ConditionalFor := "for" Expression Block
```

迭代表达式只求值一次。循环变量在每次迭代开始时绑定，在循环体外不可见。

第二个名称是从零开始的 Integer 索引，值名称始终在前：

```norm
for value,index : values {
    printLine(index)
    printLine(value)
}
```

`continue` 进入下一项时索引随迭代递增，`break` 立即结束循环。

当迭代值具有唯一、静态可知的元素类型时可以省略循环变量类型：

```norm
for index : range(start: 0, end: 10) {
    printLine(index)
}
```

`Range` 实现 `Iterable<Integer>`；`List<T>`、`Array<T>`、`Set<T>` 等从 `Iterable<T>` 的类型实参得到元素类型。只有无法得到唯一静态元素类型时才必须显式声明循环变量类型。

## 条件循环

```norm
for digits.size() > 1 && digits.last() == 0 {
    digits.removeLast()
}
```

条件必须是 Boolean，并在每轮循环开始前重新求值。条件初始为 false 时循环执行零次；`continue` 转移到下一次条件检查。执行后端在每轮检查取消状态。

## 控制转移

`continue` 结束当前迭代；不带值的 `break` 结束作为语句使用的循环。

```norm
for Integer number : numbers {
    if number < 0 { continue }
    if number == 0 { break }
    printLine(number)
}
```

## For 表达式

循环出现在值位置时，成功路径使用 `break value`，正常耗尽路径由 `else` 产生值：

```norm
Integer match = for Integer number : numbers {
    if number % 2 == 0 { break number }
} else {
    break -1
}
```

表达式循环不能使用无值 `break`。所有可达完成路径必须产生兼容类型的值，编译器不会隐式补 `null`。
