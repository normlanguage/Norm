# Norm 是什么

Norm 是一门静态强类型、面向应用开发的编程语言。它采用熟悉的类型前置与大括号语法，但重新定义了 null、赋值、共享状态、控制流表达式和运行时泛型信息。

这篇介绍只回答三个问题：Norm 解决什么问题、它选择了什么语义、接下来该读什么。

## 一个最小例子

```norm
Integer absolute(Integer value) {
    if value < 0 {
        return -value
    }
    return value
}

main() {
    Integer distance = absolute(value: -12)
    printLine("distance = ${distance}")
}
```

从这个例子可以看到 Norm 的基本外形：

- 类型写在名称前面；
- 函数可以声明在顶层；
- 多参数调用保留参数名；
- 条件不写括号，代码块使用大括号；
- 返回值必须显式 `return`。

## Norm 关心的问题

大型应用代码的主要成本通常不是写下第一版，而是长期理解和修改。Norm 希望重要语义在调用点与声明处直接可见：

- `T?` 表示一个值可能为 null；
- `ref<T>` 表示多个位置访问同一 value 存储位置；
- `Result<T, E>` 表示函数具有可预期的失败结果；
- `reflect` 表示代码进入反射或拦截边界；
- `break value` 表示控制流结构正在产生一个值。

## 核心选择

### 静态名义类型

类型关系必须通过 `implements` 或 `extends` 明确声明。具有相同方法的两个类型不会因此自动兼容。

### 非空默认

```norm
String title = "Norm"
String? subtitle = null
```

普通类型不接受 null，也没有绕过初始化检查的 `lateinit`。

### Value 与 Identity 分开

```norm
Counter first = Counter(value: 0)
Counter second = first
second.increment()
```

`first` 与 `second` 指向同一个 Counter。class 保留对象 identity；需要新对象时使用 `first.copy()`。基本类型、enum、value 和容器按 value 规则赋值。

### 低魔法

Norm 不提供宏、用户操作符重载、隐式字符串转换或任意闭包捕获。元编程能力存在，但必须通过 annotation 与 `reflect` 明确进入。

## Norm 不是什么

Norm 不优先服务内核、驱动或硬实时程序，也不追求类型级计算和极端元编程。它计划使用垃圾回收和运行时支持，让开发者把注意力放在程序结构与业务逻辑上。

Norm 当前沿 0.7 开发线演进。语言规范描述长期语义，编译器的实际边界由[版本索引](/versions/)中的最新实现契约定义。

## 接下来读什么

- 想开始学习语法：阅读[语言手册](/language/overview)；
- 想理解设计取舍：阅读[语言哲学](/guide/philosophy)；
- 想查找精确规则：进入[语言规范](/spec/language-spec)；
- 想了解实现进度：查看[版本索引](/versions/)和[项目路线图](/design/roadmap)。
