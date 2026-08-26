# 基础语法

本章建立阅读 Norm 代码所需的最小知识：声明、调用、表达式和代码块。

## 变量声明

变量声明由类型、名称和初始值组成。

```norm
Integer width = 12
Integer height = 8
String label = "rectangle"
```

类型总是出现在名称之前。非空局部变量必须在声明时初始化。

```norm
Integer count
```

上面的声明不完整，编译器会拒绝它。Norm 不提供稍后绕过检查的 `late` 或 `lateinit`。

## 表达式与语句

表达式计算一个值：

```norm
Integer area = width * height
Boolean large = area >= 100
```

语句执行一个动作，例如赋值、调用或返回：

```norm
area = area + 10
printLine(area)
return area
```

Norm 不要求行尾分号。多条语句放在大括号组成的代码块中。

## 函数声明与调用

```norm
Integer area(Integer width, Integer height) {
    return width * height
}

Integer result = area(width: 12, height: 8)
```

有结果的顶层函数把返回类型写在函数名之前；省略时固定为 `Void`。参数使用类型前置。多个参数使用命名调用，让调用点保留参数含义。

## 条件

```norm
if result > 100 {
    printLine("large")
} else {
    printLine("small")
}
```

条件不需要括号，但代码块不能省略。条件表达式必须是 `Boolean`，不会把数字或字符串隐式当作布尔值。

## 字符串组合

```norm
String greeting = "Hello, " + label
```

`+` 可以拼接 String。输出任意可打印值时直接使用 `printLine(value)` 或 `printLines(values)`。

## 创建对象

构造调用不使用 `new`：

```norm
value Point {
    Integer x
    Integer y
}

Point origin = Point(x: 0, y: 0)
```

这里的 `Point` 是一个纯数据值。关于 `value`、`class` 与共享引用的区别，请阅读[对象和值](/language/objects)。

## 一个完整例子

```norm
Integer choose(Boolean enabled, Integer preferred, Integer fallback) {
    if enabled {
        return preferred
    }
    return fallback
}

main() {
    Integer result = choose(enabled: true, preferred: 100, fallback: 0)
    printLine(result)
}
```

这个例子只依赖变量、函数、比较和条件。后面的章节会分别展开这些规则。

下一章：[类型与 Null](/language/types)。
