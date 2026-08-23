# 语言哲学

Norm 的核心判断很简单：代码首先是给人阅读的，其次才是给编译器执行的。语言应该让重要行为容易发现，而不是用更多语法糖把它们藏起来。

## 显式优于隐式

Norm 为几类高影响语义保留了醒目的语法：

| 写法 | 读者能立即知道 |
| --- | --- |
| `T?` | 这里可能没有值 |
| `name: value` | 参数含义在调用点可见 |
| `Result<T, E>` | 这是函数契约内的可预期失败 |
| `reflect` | 这里跨入反射或拦截边界 |
| `break value` | 控制流结构正在产生值 |

显式不等于冗长。它意味着代码在最需要信息的位置保留信息。

## Identity 不伪装成 Value

```norm
class Counter {
    Integer value

    Void increment() {
        value = value + 1
    }
}

Counter first = Counter(value: 0)
Counter second = first
second.increment()
```

`first.value` 是 `1`。class 具有身份，普通赋值不会隐式克隆对象。

需要新身份时写出来：

```norm
Counter copied = first.copy()
copied.increment()
```

这条规则减少的是“修改为什么从另一个地方发生”的不确定性。

## 安全不应要求类型体操

Norm 采用静态类型、非空默认、确定赋值、Result，以及明确的 value/identity 语义，但不把所有复杂性转移给类型系统使用者。

它不采用完整所有权与借用系统，也不鼓励通过复杂泛型表达业务流程。应用开发需要可靠边界，也需要普通开发者能够快速阅读代码。

## 普通行为使用普通结构

独立行为写成顶层函数；数据写成 value；具有身份和行为的状态写成 class。语言不要求把每个概念包装进 class，也不让 annotation 自动生成隐藏行为。

```norm
Double midpoint(Double left, Double right) {
    return (left + right) / 2.0
}
```

这里不需要工具类、扩展机制或代码生成。

## Norm 项目使用 Norm 配置

Norm 能表达的项目配置、模块描述、构建规则和测试定义都使用 Norm 本身。工具读取类型化的 `.norm` 对象与声明，共享语言的语法、类型检查、编辑器支持和演进规则。

项目不为同一职责并列引入 JSON、JSONC、JavaScript、TypeScript、YAML、TOML 或 INI 配置。只有必须与外部系统交换的数据才使用对方要求的格式，并在边界处转换为 Norm 类型。

## 平台不是语言规范

Norm 官方实现使用 GraalVM/Truffle，但前端、类型系统、反射模型和 Bound IR 不依赖 JVM 类型模型。

同样，Web、数据库和序列化是语言之上的应用平台。它们可以使用语言能力，却不能反过来定义基础语义。

下一步：[设计原则](/guide/design-principles)。
