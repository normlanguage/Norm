# 对象模型规范

Norm 区分 class、value、interface、enum 和 `Ref<T>`。这些类型共同使用名义类型检查，但拥有不同的可变性与 identity 规则。

## Class

class 可以拥有字段、构造器、方法和单一 class 父类型。class 值内部可变，但赋值、按值传参和返回时语义上产生独立值。

```norm
Counter second = first
second.value = 1
// first.value 不变
```

## Value

value 是构造后不可变的纯数据。相等和 hash 递归使用全部字段。value 可以实现 interface，但不能参与 class 继承。

## Interface

interface 只定义行为契约，不保存字段。满足关系必须通过 implements 或 extends 声明，不进行结构类型匹配。

## Enum

enum 是封闭 variant 集合。每个 variant 可携带不同数据，switch 可以基于完整集合进行穷尽检查。

## Ref

`Ref<Class>` 建立共享 identity。复制 Ref 继续指向同一共享单元。Ref 不可 nullable，也不能直接包装 value 或 nullable 类型。

## 动态类型

把子 class 值赋给父类型或 interface 变量时保留动态类型，不发生 object slicing。方法调用按动态类型分派，但值复制规则不变。

## 表示自由

规范不固定字段布局、对象头、GC 或引用计数。运行时可以使用写时复制、结构共享和逃逸分析，只要 equality、修改隔离、Ref identity 和反射结果符合规范。
