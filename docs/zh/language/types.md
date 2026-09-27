# 类型与 Null

Norm 是静态类型语言，默认不允许 null。`T?` 显式为类型加入 null。

`Any` 是所有非空值的静态顶类型，`Any?` 也接受 null。值可以安全地扩大为 `Any`；`Any` 不会隐式收窄成具体类型，也不暴露具体类型的成员。要求共同行为时仍应使用接口和有界泛型。

`Integer` 和 `Long` 分别是有符号 32 位、64 位整数。`Float` 与 `Double` 是二进制浮点叶类型。抽象的 `Number` 可以保存这些叶类型，并保留实际运行时表示。数字字面量优先采用期望的具体叶类型；否则整数按范围默认选择 `Integer` 或 `Long`，小数字面量默认为 `Double`。

```norm
Number count = 10
List<Number> values = [1, 2.5, 3]
```

```norm
String name = "Norm"
String? nickname = null
```

直接访问 nullable 值的成员之前必须处理 null：

```norm
if nickname != null {
  printLine(nickname.codePointSize())
}
```

提前返回与布尔短路也参与控制流敏感的类型收窄。重新赋值局部变量会更新其流状态。可变字段应先读入局部变量再收窄，使检查和使用针对同一次读取的值。

## 安全访问与回退

```norm
Integer? citySize = user.address?.city?.codePointSize()
String displayName = user.nickname ?? user.name
```

安全访问只求值一次接收者；接收者为 null 时跳过成员调用及其实参。Null 合并运算符仅在左侧为 null 时才求值右侧。

## 泛型组合

```norm
List<String>? optionalNames = null
List<String?> names = ["Norm", null]
```

可空性作用于紧邻的完整类型。泛型替换会规范化重复可空性，因此在 `T?` 中以 `String?` 替换 `T`，结果仍是 `String?`。

`null` 需要 nullable 期望类型；它自身无法推断任意类型。

下一篇：[值与身份](/zh/language/objects)。
