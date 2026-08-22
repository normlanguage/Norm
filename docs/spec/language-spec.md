# Norm 语言规范

本文是 Norm 1.0 核心语言规则的入口。手册解释如何使用语言，本规范定义编译器必须接受、拒绝和执行什么。

> Norm 当前处于规范草案阶段。标为“待定”的内容不能被实现或文档当作稳定承诺。

## 设计边界

Norm 是静态、名义、非空默认的语言，核心差异集中在三条具体规则：

1. class 保留对象 identity，内建容器保持 value 语义，显式 `copy()` 创建新的顶层对象；
2. if、for、switch 作为表达式时使用 `break value` 显式产生结果；
3. 泛型参数在运行时保留，不使用类型擦除。

语言不提供宏、操作符重载、隐式字符串转换、隐式 nullable、raw type 或隐式 Result 传播。

## 源文件与模块

源码使用 UTF-8。项目文件先声明 package，随后是 import 和顶层声明；没有 package 的文件作为单文件脚本运行。顶层允许类型和函数，不需要 static 工具 class。

```norm
package geometry

import std.math.sqrt

double length(Point point) {
    return sqrt(value: point.x * point.x + point.y * point.y)
}
```

## 声明

类型写在名称之前：

```norm
String name = "Ada"
int age = 36

int square(int value) {
    return value * value
}
```

核心声明包括 class、value、interface、enum、annotation 和 function。public/private 控制可见性；更细模块可见性仍待定。

## 类型系统

类型关系由 extends 和 implements 明确声明，不根据成员形状自动匹配。普通 `T` 不包含 null，`T?` 才包含。编译器执行确定赋值和控制流 null 收窄。

Norm 没有统一 Object 根类型。泛型约束和 interface 表达通用行为。

## 值模型

class 可变且具有身份；赋值、传参和返回共享同一对象。基本类型、enum 和内建容器是 value。`class.copy()` 创建新的顶层对象，value 使用结构相等，class 使用身份相等。完整定义见 [Value 与 Identity 语义](/spec/value-identity-semantics)。

`ref<T>` 引用 value 的存储位置，不是 class 共享入口。

## 控制流

if、for 和 switch 可以作为语句，也可以作为表达式。表达式路径必须显式产生值：

```norm
String sign = if number < 0 {
    break "negative"
} else {
    break "non-negative"
}
```

不会把最后表达式自动作为结果，也不会为缺失分支插入 null。for 采用 foreach 形状；首版没有 C 风格 for 和 while。

## 泛型

泛型默认不变，使用时必须写全类型实参。`? extends T` 和 `? super T` 表达使用位置型变。运行时保留完整参数化类型：

```norm
List<String>.class != List<int>.class
```

## 错误

可预期失败使用 Result 或 Option；异常使用 throw/try/catch/finally。Result 是普通 enum，语言不提供自动传播。资源清理必须在所有完成路径上可见或由标准库作用域 API 保证。

## 求值

子表达式和实参按源码从左到右求值；命名参数只改变形参绑定，不改变求值顺序。逻辑运算短路。优化器可以消除 value 复制或共享内部存储，但不能改变对象身份、I/O 顺序或动态类型。

## 规范导航

- [语法总览](/spec/grammar/overview)
- [类型系统](/spec/type-system)
- [Value 与 Identity 语义](/spec/value-identity-semantics)
- [对象模型](/spec/object-model)
- [内存语义](/spec/memory-semantics)
- [表达式语义](/spec/expression-semantics-formal)
- [形式语义](/spec/formal/semantics)
- [编译器设计](/spec/compiler-design)
