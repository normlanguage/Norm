# 类型推断形式规则

类型推断是一个受限约束求解过程。它只补充调用位置能够唯一确定的信息，不替代公开声明的参数和返回类型。

## 约束来源

编译器为每个候选建立独立的求解会话，并收集：

- 实参到形参的可赋值约束；
- 赋值目标或 return context 提供的期望类型；
- 类型参数声明的 extends 上界；
- nullable 、interface 约束和名义协议投影产生的类型关系；
- 序列字面量的目标容器与元素类型；
- diamond 构造器的 owner 类型参数；
- 数字字面量对具体数值叶类型的可表示约束。

## 求解

1. 解析名称与候选重载；
2. 从赋值、返回值或参数位置向表达式传播 expected type；
3. 从表达式自身类型反向收集约束；
4. 合并相等、子类型、nullable 与名义 conformance 约束；
5. 求解泛型参数并实例化嵌套表达式；
6. 最后确定数字字面量的具体类型；
7. 验证全部实参并选择唯一最佳候选。

```norm
T identity<T>(T value) { return value }
String name = identity(value: "Norm")
```

这里得到约束 `T = String`。

```norm
List<Pair<Integer, String>> values = List<>()
values.add(Pair<>(first: 7, second: "seven"))
```

外层集合元素类型向 `Pair<>` 传播，得到 `A = Integer` 与 `B = String`。数字 `7` 在求解完成后按 Integer 物化。推断只沿显式类型关系传播，不搜索附近出现的类型名称。

## 拒绝条件

编译器不使用隐式数值收窄、任意联合类型、函数体分析或运行时值来完成推断。`null` 没有独立的具体类型；缺少期望 nullable 类型时无法推断。空 `[]` 和无参 `List<>()` 同样需要外部约束。失败诊断列出未解决类型变量和冲突约束。
