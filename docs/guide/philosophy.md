# 语言哲学

Norm 的核心判断很简单：代码首先是给人阅读的，其次才是给编译器执行的。语言应该让重要行为容易发现，而不是用更多语法糖把它们藏起来。

## 显式优于隐式

Norm 为几类高影响语义保留了醒目的语法：

| 写法 | 读者能立即知道 |
| --- | --- |
| `T?` | 这里可能没有值 |
| `Ref<T>` | 这里存在共享 identity |
| `Result<T, E>` | 这是函数契约内的可预期失败 |
| `reflect` | 这里跨入反射或拦截边界 |
| `break value` | 控制流结构正在产生值 |

显式不等于冗长。它意味着代码在最需要信息的位置保留信息。

## 默认不共享

```norm
class Counter {
    int value

    void increment() {
        value = value + 1
    }
}

Counter first = Counter(value = 0)
Counter second = first
second.increment()
```

`first.value` 仍然是 `0`。普通赋值不会意外连接两个可变对象。

共享必须写出来：

```norm
Ref<Counter> shared = first.ref()
shared.increment()
```

这条规则减少的是“修改为什么从另一个地方发生”的不确定性。

## 安全不应要求类型体操

Norm 采用静态类型、非空默认、确定赋值、Result 和值语义，但不把所有复杂性转移给类型系统使用者。

它不采用完整所有权与借用系统，也不鼓励通过复杂泛型表达业务流程。应用开发需要可靠边界，也需要普通开发者能够快速阅读代码。

## 普通行为使用普通结构

独立行为写成顶层函数；数据写成 value；带行为的状态写成 class；共享状态使用 Ref。语言不要求把每个概念包装进 class，也不让 annotation 自动生成隐藏行为。

```norm
double midpoint(double left, double right) {
    return (left + right) / 2.0
}
```

这里不需要工具类、扩展机制或代码生成。

## 平台不是语言规范

Norm 初期计划使用 GraalVM/Truffle，但前端、类型系统、反射模型和 Typed IR 不依赖 JVM 类型模型。

同样，Web、数据库和序列化是语言之上的应用平台。它们可以使用语言能力，却不能反过来定义基础语义。

下一步：[设计原则](/guide/design-principles)。

