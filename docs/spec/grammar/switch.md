# Switch 语法

`switch` 对 enum variant、常量或运行时类型进行分支。每个 `case` 都使用代码块，表达式形式通过 `break value` 产生结果。

```norm
String text = switch token {
    case Number(double value) { break "number ${value}" }
    case Name(String value) { break value }
    case End { break "end" }
}
```

## 匹配顺序

case 按源码顺序测试。variant pattern 同时检查构造器并绑定数据；绑定名称只在当前 case 的代码块中有效。

## 穷尽性

对封闭 enum 的表达式 switch，必须覆盖全部 variant。语句 switch 可以省略分支，但编译器应给出可配置警告。新增 enum variant 后，不完整的表达式 switch 会在编译期失败。

开放类型层次不能静态枚举所有子类，需要显式兜底分支：

```norm
String kind = switch shape {
    case Circle circle { break "circle" }
    case else { break "other" }
}
```

`case else` 必须是最后一个分支。常量 case 不允许重叠，已经被前序模式完全覆盖的分支不可达并产生编译错误。

