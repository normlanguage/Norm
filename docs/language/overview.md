# 语法总览

Norm 使用类型前置、大括号、可省略分号，控制流不写括号。

```norm
String name = "Alice"
int age = 20

if age >= 18 {
    print("${name} is adult")
}
```

主要结构：`class`、`value`、`interface`、`enum`、`if`、`for`、`switch`、`break`、`continue`、`return`、`try/catch/finally`、`annotation`、`reflect`、`is`、`as`、`this`、`super`。

Norm 没有 `static`；不依赖实例的行为写成顶层函数。
