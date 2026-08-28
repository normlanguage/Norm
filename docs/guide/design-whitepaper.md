# Norm 语言设计白皮书

## 摘要

Norm 是一门静态强类型、面向应用开发的编程语言。它希望保留 Java、Kotlin、C# 一类语言容易阅读和工程化的部分，同时重新定义几条长期影响程序可维护性的语义边界：值与对象身份、null、控制流产值、泛型运行时表示、框架 metadata、系统资源以及编译产物身份。

Norm 不追求最多的语言功能。它追求较少但能稳定组合的概念，并要求编译器、编辑器、运行时和原生发行共同实现同一份程序含义。

## 1. 设计问题

大型应用的复杂度经常来自局部代码没有携带足够信息。

一次普通赋值可能复制结构、共享对象或引用某个可变位置；函数失败可能使用 null、状态码、Result、异常或框架包装；泛型在编译后可能消失；Annotation 可能只是 metadata，也可能在运行时改写调用；编辑器和构建工具还可能各自实现不同的名称解析规则。

这些问题单独看都能靠文档解释，组合后却会不断提高理解成本。Norm 的设计目标是让高影响语义进入语言的类型、声明和调用结构，而不是留给习惯或框架约定。

## 2. 语言定位

Norm 主要服务后端服务、业务系统、桌面应用、命令行工具和共享应用库。它采用垃圾回收和运行时支持，不要求普通应用开发者管理对象生命周期或证明借用关系。

语言使用名义静态类型。类型关系必须通过 `extends` 或 `implements` 声明，成员形状相同不会自动建立兼容关系。普通类型非空，nullable 使用 `T?` 明确表示。局部变量和字段必须经过确定赋值，类型转换与 null 收窄遵守静态规则。

Norm 不把极端元编程、内核开发、硬实时执行或类型级计算作为核心目标。这个边界让语言可以把工程可读性、诊断和应用运行时放在更高优先级。

## 3. 数据模型

### 3.1 Class

`class` 表达具有 identity 的对象。class 可以包含可变字段、方法、构造器、单继承和 interface conformance。

```norm
class Session {
    String state

    activate() {
        state = "active"
    }
}
```

class 变量保存对象引用。赋值、传参和返回保留同一对象身份，`==` 使用身份相等。`copy()` 创建新的顶层对象；若字段仍指向其他 class，对象副本继续共享这些嵌套身份。

### 3.2 Value

`value` 表达由内容定义的结构数据。

```norm
value Money {
    Decimal amount
    String currency
}
```

value 字段在构造后不可重新赋值，赋值和调用边界产生逻辑独立的值，`==` 与 hash 递归使用字段语义。编译器和运行时可以消除复制或共享内部存储，只要程序无法观察到 identity。

基本类型、enum 和内建容器同样属于 value 世界。容器复制自身结构；容器内的 class 元素仍保留各自对象身份。

### 3.3 Ref

`ref<T>` 引用 value 的存储位置。它不接受 class，也不承担对象共享。

```norm
Integer cursor = 0
ref<Integer> location = &cursor
*location = 8
```

这三种类别分别回答“数据是什么”“对象是谁”和“值存在哪里”，避免让一个通用引用模型承担互相冲突的语义。

完整规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。

## 4. 函数与调用

函数是顶层语言结构，不需要放进 class。package 组织声明，class 表达对象，函数表达不依赖对象状态的行为。

```norm
Integer clamp(Integer value, Integer minimum, Integer maximum) {
    if value < minimum {
        return minimum
    }
    if value > maximum {
        return maximum
    }
    return value
}

Integer opacity = clamp(value: input, minimum: 0, maximum: 100)
```

多参数调用使用名称绑定。参数名属于公开调用约定，实参表达式仍按源码从左到右求值。

Class 方法访问对象状态，并参与动态分派。省略返回类型的方法是返回同一接收者的 fluent 方法；真正无结果的方法显式写 `Void`。

Extension function 允许显式导入的顶层函数使用点号形式：

```norm
extension String quoted(String value) {
    return "\"" + value + "\""
}

String text = "Norm".quoted()
```

它不修改目标类型，不进入动态方法表。真实实例方法优先，extension 候选继续使用普通静态重载规则。

## 5. 控制流与结果

Norm 1.0 规范让 `if`、`for` 和 `switch` 都可以产生值，表达式路径必须显式给出结果。当前发布版已经实现其中的穷尽 switch 表达式，其余实现边界以版本记录为准。

```norm
String describe(Token token) {
    return switch token {
        case Name(String text) { break text }
        case End { break "end" }
    }
}
```

Norm 不把代码块的最后一个表达式隐式当作结果，也不会为不完整路径补 null。`for` 表达式使用 `else` 处理正常耗尽，`switch` 必须穷尽且不会 fallthrough。

Enum variant 可以携带数据，switch 通过模式解构 payload。`Result<T, E>` 就是使用该能力定义的普通泛型 enum，而不是编译器内置的特殊控制流。

## 6. 泛型与运行时类型

Norm 泛型保持不变，类型位置写全实参，构造和泛型调用可以根据期望类型与实参求解。求解后的实际类型参数进入 canonical Core 和运行时类型环境，不采用类型擦除。

Reified 类型模型服务动态分派、反射、Annotation、serialization 和运行时诊断。公共反射入口使用类型字面量：

```norm
Class<Order> type = Order.class
List<Field<Order, ?>> fields = type.fields()
```

字段声明引用具有稳定 identity、owner、声明类型和 runtime Annotation。`Field<Owner, Value>.read(Owner)` 直接返回精确的 Value，不会根据字符串搜索 getter 或依赖 JVM reflection。

## 7. Annotation 与受控扩展

Annotation 是 Norm 对象模型中的 identity aggregate。它通过普通 interface 声明可应用目标、metadata 保留和可选生命周期。

只提供 metadata 的 Annotation 可以实现 `TypeTarget`、`FieldTarget` 等目标 interface 与 `RuntimeRetention`。需要参与执行时，Annotation 显式实现 `FunctionInterceptor`、`ParameterInterceptor<T>` 或 `FieldInterceptor<T>`。

这种分层区分三件事：Annotation 能标在哪里，metadata 保留多久，以及它是否真的执行行为。生命周期在定义侧进入普通调用、构造、动态分派和函数引用的统一入口，不依赖调用点代理或运行时扫描。

Norm 不提供宏、编译期代码派生 DSL 或运行时代码注入。Validation 使用强类型参数/字段生命周期，serialization 使用 passive metadata；两者共享 Annotation 模型，不共享不必要的执行机制。

## 8. 缺失、失败与资源

Norm 根据调用方责任区分三类情况：

- nullable 表达普通缺失；
- `Result<T, E>` 表达业务契约内的可预期结果分支；
- Exception 表达无法正常完成的系统、协议和运行时状态。

语言不提供隐式 Result 传播。Exception 使用 `throw`、`try`、`catch` 和 `finally`，系统标准库抛出带稳定 code、operation 和 reason 的领域异常。

外部资源通过 `Resource`、`ByteReader`、`ByteWriter` 与作用域 `use` API 管理。读取完整内容必须提供上限，HTTP response body 与文件流进入同一确定性关闭模型。取消与 timeout 由执行上下文和平台 adapter 传递，不依赖全局服务定位器。

## 9. 标准库与应用边界

Norm 标准库的公开 API 使用 Norm 编写，宿主能力通过后端无关的 system contract 接入，JDK adapter 是当前官方平台实现。

HTTP 核心 request body 是 `Bytes`，不依赖 JSON。`std.http` 的 JSON 组合调用 `std.serialization`，因此协议传输和数据格式可以独立演进。

结构序列化由 `DataMapper`、`DataReader<T>` 和 `DataWriter<T>` 定义统一入口。JSON、XML 与 YAML 共享 exact Core type shape、字段访问和规范构造路径，各格式只实现 token、格式 metadata 与错误映射。自动映射显式标记的 value；class 对象图、循环引用和多态需要独立 identity 协议。

Web server、数据库和依赖注入属于应用平台，不进入核心语言语义。当前可用标准库以[标准库索引](/stdlib/overview)和[版本记录](/versions/)为准。

## 10. 编译与执行架构

官方工具链使用一条确定的语义管线：

```text
Norm Source
    → Lexer / Parser
    → SemanticModel
    → Binder
    → Canonical Core IR
    → Truffle Backend
    → JVM execution / JIT / Native Image
```

SemanticModel 是名称解析、类型检查、调用目标、泛型实例化、可见性和编辑器 authoring 信息的唯一结果。Binder 将已验证语义冻结为确定引用，Core 不再重新解析源码名称。

Canonical Core 使用内容寻址的 definition identity。公开 ABI、代码、runtime metadata、调试信息和最终 executable 按各自真实依赖建立身份，支持精确增量失效、跨进程 definition store 和 Truffle artifact 复用。

Truffle 是唯一官方执行后端。Native Image 打包同一个 Core 与 Truffle 执行实现，并不是另一套语言编译器。CLI、Language Server、测试入口和项目加载共享同一 project system 生命周期。

详细架构见[编译器架构](/spec/compiler-design)与[实现策略决议](/design/implementation-strategy)。

## 11. 工具与发行

官方 VS Code 扩展只负责编辑器集成，诊断、补全、签名、格式化、导航和重命名都由 `norm lsp` 及编译器语义快照提供。扩展不维护第二套语言规则。

Tagged Release 为 Windows x64、Linux x64 与 macOS ARM64 生成独立 CLI，并把所有受支持平台的同版本 CLI 打进一个通用 VSIX。每个平台运行相同的语言验收、LSP smoke 和 Extension Host 测试后，发布任务才生成校验和与构建证明。

## 12. 规范与实现

Norm 语言规范面向 1.0 长期语义，版本记录定义当前发布版已经实现的边界。规范中的稳定目标不自动等于当前产品能力，发布版也不能用未记录的实现行为扩展语言。

这一区分让语言设计可以提前建立完整方向，同时让用户基于可执行、经过验收的版本契约做决定。

## 结论

Norm 的核心不是某一个语法功能，而是一组互相支持的边界：value 不携带 identity，class 不伪装成复制，ref 只引用 value 存储；控制流显式交出结果；泛型类型进入运行时；Annotation 和 extension 仍服从普通类型与调用规则；系统资源和数据格式通过强类型标准库进入唯一执行后端。

这些选择共同服务于一件事：让应用代码在规模增长以后，仍然可以从源码判断它会怎样运行。

下一篇：[比较、取舍与发展方向](/guide/comparison-and-future)。
