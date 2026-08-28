# Norm 是什么

Norm 是一门静态强类型、面向应用开发的编程语言。它看起来接近 Java、Kotlin 或 C# 一类大括号语言：类型写在名称前面，程序由函数、class、interface、enum 和 package 组成。真正不同的地方不在标点，而在几条贯穿语言、标准库和工具链的语义边界。

- 结构数据使用 `value`，有状态对象使用 `class`，value 的存储位置才使用 `ref<T>`；
- 普通类型非空，switch 产值必须写出 `break value`；
- 多参数调用保留参数名，extension function 保持静态解析；
- 泛型实参进入运行时，反射和序列化读取同一份 Core 类型信息；
- Annotation 可以提供 metadata 或实现强类型生命周期，但不能像宏一样任意改写程序。

这些选择服务于一个目标：重要行为应当能从源码直接推断。

## 一段完整的 Norm 代码

```norm
import std.json.toJson
import std.serialization.SerialName
import std.serialization.Serializable

enum Audience { Adult, Minor }

@Serializable()
value Profile {
    @SerialName(name: "display_name")
    String name
    Audience audience
}

String label(Audience audience) {
    return switch audience {
        case Adult {
            break "adult"
        }
        case Minor {
            break "minor"
        }
    }
}

main() {
    Profile profile = Profile(
        name: "Ada",
        audience: Audience.Adult
    )
    printLine(label(profile.audience))
    printLine(profile.toJson())
}
```

这段代码没有依赖生成代码或运行时字符串约定。

`Profile` 是结构化 value，构造后字段不可重新赋值。构造器使用命名参数，调用点保留每个实参的含义。穷尽的 `switch` 通过 `break` 明确交出结果。`toJson()` 来自显式导入的 extension function，静态解析为普通顶层函数调用。`@Serializable` 和 `@SerialName` 提供运行时 metadata，JSON mapper 根据精确类型与字段 ordinal 读取值。

## 数据类别是语言规则

### `value` 表达结构数据

```norm
value Coordinate {
    Integer x
    Integer y
}

Coordinate first = Coordinate(x: 2, y: 4)
Coordinate second = first
```

两个变量保存逻辑独立的结构值。value 使用结构相等，适合请求、配置、金额、坐标和其他由内容定义的数据。

### `class` 表达对象身份

```norm
class Counter {
    Integer value

    increment() {
        value = value + 1
    }
}

Counter first = Counter(value: 0)
Counter second = first
second.increment()
printLine(first.value)
```

输出是 `1`。class 变量保存对象引用，赋值、传参和返回都会保留同一 identity。需要新的顶层对象时显式调用 `copy()`。

### `ref<T>` 表达存储位置

`ref<T>` 不负责 class 共享。它让代码引用一个 value 的可寻址位置，并通过解引用替换该位置保存的值。这使“共享对象”和“共享变量位置”成为两种不同的类型关系。

完整规则见 [Class、Value 与 Identity](/language/objects)。

## 显式不等于冗长

Norm 会删除没有信息量的样板，例如顶层函数不需要工具 class，class 方法可以直接访问字段，类型参数可以从构造上下文推断。与此同时，它会保留影响理解的信息。

```norm
Integer distance = subtract(left: end, right: start)
String? label = null
```

参数标签说明每个值绑定到哪个形参，`?` 说明缺失是类型的一部分。类似地，switch case 不把最后一个表达式自动当作结果，也不会为缺失 variant 补 `null`。

## 失败按语义分层

Norm 使用三种不同结构表达失败：

| 情况 | 表达方式 |
| --- | --- |
| 一个值可能不存在 | `T?` |
| 业务流程存在调用方需要处理的结果分支 | `Result<T, E>` |
| 系统、协议或运行时无法正常完成操作 | 类型化 Exception |

`Result` 是普通泛型 enum，没有特殊传播运算符。文件、网络、序列化等系统边界抛出带领域信息的 Exception。语言不会把这三种情况压缩成一个万能返回类型。

## 运行时保留类型结构

Norm 的泛型不是只供编译器检查的表面语法。实际类型参数进入 Core IR 和运行时类型环境，因此 `List<String>` 与 `List<Integer>` 在需要类型信息的边界仍然可区分。

```norm
Type<Profile> type = reflect<Profile>()
List<Field<Profile>> fields = type.fields()
ReflectedValue name = fields[0].read(receiver: profile)
```

结构反射读取 Norm 编译器生成的 metadata，不遍历 JVM class，也不通过字段名调用方法。JSON、XML 与 YAML 的自动映射复用同一份类型和字段结构。

## 当前实现已经具备什么

官方发行版提供独立的 Windows、Linux 和 macOS CLI，以及包含对应原生 CLI 的通用 VS Code 扩展。当前工具链已经覆盖：

- Parser、名义类型检查、确定赋值、泛型、class/value/ref、enum、异常与穷尽 switch 表达式；
- Formatter、诊断、补全、签名提示、导航、引用、重命名和运行命令；
- 流式 I/O、文件读写、时间、HTTP 客户端与确定性资源关闭；
- 基于统一 `DataMapper` 的 JSON、XML 与 YAML 双向结构映射；
- Annotation metadata、函数/参数/字段拦截生命周期与字段反射；
- canonical Core IR、Truffle 执行和 GraalVM Native Image 发行。

精确范围以[最新版本记录](/versions/)为准。长期语言目标与当前实现不是同一份承诺。

## 适合怎样的工作

Norm 的目标是服务需要长期维护的应用程序：后端服务、业务系统、命令行工具、桌面应用及其共享基础库。它选择垃圾回收、名义类型和受控运行时能力，把工程可读性放在手动内存管理和类型级元编程之前。

当前生态和平台覆盖仍处于早期阶段。评估真实项目时，应同时阅读[比较、取舍与发展方向](/guide/comparison-and-future)和[版本记录](/versions/)，不要只根据长期规范判断可用性。

下一篇：[VS Code 开发体验](/guide/vscode)。继续系统学习语法可直接进入[语言手册](/language/overview)。
