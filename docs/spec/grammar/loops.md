# 循环语法

Norm 只提供 foreach 形状的 `for`。没有 C 风格计数循环，也没有 `while`；范围和无限序列由实现迭代协议的普通值提供。

```norm
for String name : names {
    print(name)
}
```

## 语法形状

```text
For := "for" Type? Identifier ":" Expression Block ("else" Block)?
```

迭代表达式只求值一次。循环变量在每次迭代开始时绑定，在循环体外不可见。

当迭代值具有唯一、静态可知的元素类型时可以省略循环变量类型：

```norm
for index : range(start: 0, end: 10) {
    print(index)
}
```

`Range` 的元素类型是 `int`；`List<T>`、`Array<T>`、`Set<T>` 等迭代值从类型参数得到元素类型。只有无法得到唯一静态元素类型时才必须显式声明循环变量类型。

## 控制转移

`continue` 结束当前迭代；不带值的 `break` 结束作为语句使用的循环。

```norm
for int number : numbers {
    if number < 0 { continue }
    if number == 0 { break }
    print("${number}")
}
```

## For 表达式

循环出现在值位置时，成功路径使用 `break value`，正常耗尽路径由 `else` 产生值：

```norm
int match = for int number : numbers {
    if number % 2 == 0 { break number }
} else {
    break -1
}
```

表达式循环不能使用无值 `break`。所有可达完成路径必须产生兼容类型的值，编译器不会隐式补 `null`。

