# Norm 语言手册

这本手册描述 Norm 1.0，面向第一次系统学习 Norm 的读者。它解释日常编程需要的语言功能，并通过短小、可独立理解的例子展示语义。

手册不是完整规范。遇到边界情况、编译器约束或形式化定义时，请查阅[语言规范](/spec/language-spec)。标准库 API 和 Web 平台也有各自独立的文档。

## 如何阅读

如果你第一次接触 Norm，建议按左侧目录从上到下阅读：

1. [基础语法](/language/basics)介绍程序结构、表达式和命名习惯。
2. [类型与 Null](/language/types)解释静态类型、转换和非空默认。
3. [Class、Value 与 Identity](/language/objects)介绍 Norm 最重要的数据模型。
4. [函数](/language/functions)和[控制流](/language/control-flow)覆盖日常行为组织。
5. [接口](/language/interfaces)、[Enum 与 Switch](/language/enum-switch)和[泛型](/language/generics)用于构建可复用抽象。
6. [错误处理](/language/errors)说明 `Result<T, E>` 与 Exception 的边界。

已经熟悉静态类型语言的读者，可以先阅读本页的“关键差异”，再按需跳转。

## 第一个程序

```norm
void main() {
    String language = "Norm"
    print("Hello, ${language}")
}
```

Norm 使用类型前置声明和大括号。行尾分号可以省略，控制流条件不写括号。

```norm
int temperature = 18

if temperature < 20 {
    print("cool")
}
```

## 关键差异

### 非空是默认规则

```norm
String title = "Guide"
String? subtitle = null
```

`String` 不能保存 null。只有带 `?` 的 `String?` 明确表示“可能没有值”。

### 赋值遵循数据类别

```norm
class Counter {
    int value
}

Counter first = Counter(value: 1)
Counter second = first
second.value = 2
```

修改 `second` 也会通过 `first` 被观察到，因为 class 变量保留对象 identity。需要新对象时使用 `first.copy()`；value 与容器仍产生逻辑独立值。

### 控制流产生值时必须写明

```norm
String sign = if number < 0 {
    break "negative"
} else {
    break "non-negative"
}
```

Norm 不使用“最后一个表达式就是结果”的规则。`break value` 明确指出值从哪个分支产生。

### 顶层函数不需要 class

```norm
int square(int value) {
    return value * value
}
```

Norm 没有 `static`。不依赖对象状态的行为直接写成顶层函数。

## 手册范围

这本手册只解释语言本身：类型、值、函数、控制流和抽象机制。HTTP、数据库、依赖注入、序列化等内容属于[应用平台文档](/web/overview)，不会作为基础语法的前置知识。

下一章：[基础语法](/language/basics)。

