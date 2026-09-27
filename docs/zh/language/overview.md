# Norm 语言手册

本手册介绍 Norm 相比其他静态类型应用语言最有辨识度的语法。它是学习指南，不是完整规范。

## 第一个程序

```norm
Integer square(Integer value) {
    return value * value
}

main() {
    Integer result = square(4)
    printLine(result)
}
```

Norm 使用类型前置声明、大括号、可省略的分号，以及多参数调用的具名实参。标签使用 `name: value`；单参数可以省略标签，同名标识符可以缩写对应标签。函数可以在 package 级声明，不需要 `static` 关键字。

## 三项核心特征

### 值与身份语义

```norm
Counter second = first
Counter copied = first.copy()
```

第一项保留对象身份。第二项创建新的顶层对象身份。

### 控制流显式产出值

```norm
String sign = if number < 0 {
    break "negative"
} else {
    break "non-negative"
}
```

Norm 不会隐式把代码块的最后一个表达式作为结果。`break value` 明确标出结果产生的位置。

### 依上下文推断泛型

```norm
List<String> names = List<>()
```

期望类型与表达式类型共同决定省略的泛型实参。

## 阅读顺序

1. [值与身份](/zh/language/objects)
2. [控制流表达式](/zh/language/control-flow)
3. [保留运行时类型的泛型](/zh/language/generics)

[Language Tour](/zh/learn/)还涵盖基本语法、可空性、函数、接口、枚举、错误和反射。
