# Language Reference

本页是 Norm 核心语言规则的索引。Language Tour 解释如何使用语言，Reference 定义编译器必须接受、拒绝和执行什么。

当前发布版尚未实现的语法不会写成可用规则；版本成熟度和限制统一列在 [Status](/status)。

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

Integer coordinateSum(Point point) {
    return point.x + point.y
}
```

## 声明

类型写在名称之前：

```norm
String name = "Ada"
Integer age = 36

Integer square(Integer value) {
    return value * value
}
```

核心声明包括 class、value、interface、enum、annotation 和 function。interface 是唯一的名义行为抽象；标准库 protocol 只是普通 interface。声明默认 `public`，`private` 限制在声明文件内；跨 package 和跨模块可见性由 `module.norm` 的 exports 与直接依赖共同确定。

顶层函数省略返回类型时是 `Void`。class 方法省略返回类型时返回同一接收者，真实签名使用完整 owner 类型。显式 `Void` 不产生结果。

## 类型系统

类型关系由 extends 和 implements 明确声明，不根据成员形状自动匹配。普通 `T` 不包含 null，`T?` 才包含。编译器执行确定赋值和控制流 null 收窄。

Norm 没有统一 Object 根类型。泛型约束和 interface 表达通用行为。

## 值模型

class 可变且具有身份；赋值、传参和返回共享同一对象。基本类型、enum 和内建容器是 value。`class.copy()` 创建新的顶层对象，value 使用结构相等，class 使用身份相等。完整定义见 [Value 与 Identity 语义](/spec/value-identity-semantics)。

`ref<T>` 引用 value 的存储位置，不是 class 共享入口。完整边界见 [`ref<T>` 引用语法](/spec/grammar/references)。

## 控制流

if、for 和 switch 可以作为语句，也可以作为表达式。表达式路径必须显式产生值：

```norm
String sign = if number < 0 {
    break "negative"
} else {
    break "non-negative"
}
```

控制流表达式不会把最后表达式自动作为结果，也不会为缺失分支插入 null。每个 switch 都必须穷尽，被匹配表达式只求值一次且 case 不 fallthrough。Lambda 的末尾表达式规则见[高级函数规则](/spec/grammar/functions-advanced)。遍历式 for 通过标准库 Iterable interface 工作；当前语法没有 C 风格 for 和 while。

## 泛型

泛型保持不变，类型位置必须写全实参。表达式中的菱形构造器可以由期望类型和构造参数求解实参；求解结果进入 Core IR 和运行时类型环境。

## 错误

普通缺失使用 nullable，可预期且需要错误原因的失败使用 `std.core.Result<T, E>`；没有业务值的成功结果使用 `std.core.Unit`。Result 是普通泛型 enum，语言不提供自动传播。异常使用 throw/try/catch/finally；资源清理必须在所有完成路径上可见或由标准库作用域 API 保证。

## 求值

子表达式和实参按源码从左到右求值；命名参数只改变形参绑定，不改变求值顺序。逻辑运算短路。优化器可以消除 value 复制或共享内部存储，但不能改变对象身份、I/O 顺序或动态类型。

## 规范导航

- [语法总览](/spec/grammar/overview)
- [类型系统](/spec/type-system)
- [Value 与 Identity 语义](/spec/value-identity-semantics)
- [Package 与模块](/spec/module-system)
- [引用生命周期](/spec/grammar/references)
- [Annotation 语义](/spec/annotations)
- [当前限制](/status)
- [对象模型](/spec/object-model)
- [内存语义](/spec/memory-semantics)
- [表达式语义](/spec/expression-semantics-formal)
- [形式语义](/spec/formal/semantics)
- [编译器设计](/spec/compiler-design)
