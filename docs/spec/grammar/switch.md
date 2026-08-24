# Switch 语法

`switch` 对 enum variant、字面量、null 或运行时名义类型进行分支。每个 `case` 都使用代码块，表达式形式通过 `break value` 产生结果。

```norm
String text = switch token {
    case Number(Double value) { break "number" }
    case Name(String value) { break value }
    case End { break "end" }
}
```

## 匹配顺序

被匹配表达式只求值一次。case 按源码顺序测试，首个匹配 case 独占执行；case 之间没有 fallthrough。完整模式规则见[模式匹配](/spec/grammar/patterns)。

## 穷尽性

每个 switch 都必须穷尽。编译器根据静态类型与模式递归计算覆盖范围：封闭 enum 必须覆盖全部 variant，nullable 类型必须覆盖 null，无法有限枚举的值域与开放名义类型必须以 `_` 覆盖剩余值。遗漏分支是编译错误。

开放类型层次不能静态枚举所有子类，需要显式兜底分支：

```norm
String kind = switch shape {
    case Circle circle { break "circle" }
    case _ { break "other" }
}
```

`_` 覆盖当前类型仍未匹配的全部值。已经被前序模式完全覆盖的分支不可达并产生编译错误。

## 完成规则

语句 switch 的 case 可以正常完成，随后结束整个 switch。表达式 switch 的每条 case 路径若能正常完成，必须执行 `break value`；`return` 或 `throw` 等不正常完成路径不需要产生局部结果。各 `break value` 的结果必须合并为唯一静态类型。

