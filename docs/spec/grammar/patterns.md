# 模式匹配

模式只出现在 switch case 等明确的匹配位置，用于测试形状并绑定局部名称。它不是任意布尔表达式。

## 模式形式

首版模式包括 variant、类型化绑定、通配符 `_`、字面量和 `null`。variant 的每个数据位置再次接受完整模式，因此模式可以递归嵌套。

```norm
enum Tree<T> {
    Leaf(T value),
    Branch(Tree<T> left, Tree<T> right)
}

case Branch(Leaf(Integer value), _) {
    printLine(value)
}
```

variant 名必须属于该位置的 enum。参数数量和顺序必须与 variant 声明一致。`Integer value` 是类型化绑定：模式成功时，以声明类型把匹配值绑定到 `value`；名称只在当前 case 块内可见。

`_` 匹配任意值且不绑定名称。字面量按该类型的语言内建相等语义匹配，且必须与所在位置的静态类型兼容。`null` 只匹配 nullable 位置的 null 值。

```norm
case Leaf(0) { printLine("zero") }
case Leaf(null) { printLine("missing nullable value") }
case _ { printLine("other") }
```

类型化绑定可以使用被匹配值的静态类型或其名义子类型；使用子类型时检查动态类型并绑定收窄后的值。成员形状不参与匹配。

## 匹配过程

单个模式由外到内、同层从左到右检查。失败不会留下局部绑定或其他可观察状态。case 按源码顺序选择首个匹配模式；被前序模式完全覆盖的 case 不可达并产生编译错误。模式只检查 enum variant、名义类型和值，不调用用户定义的匹配协议。
