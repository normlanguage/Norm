# 类型与 Null

Norm 是静态强类型语言。编译器在程序运行前确定每个表达式的类型，并拒绝不安全的隐式转换。

## 基本类型

核心数值类型包括：

| 类别 | 类型 | 用途 |
| --- | --- | --- |
| 整数 | `Byte`、`Short`、`Integer`、`Long` | 不同范围的整数 |
| 浮点数 | `Float`、`Double` | 二进制浮点计算 |
| 十进制 | `Decimal` | 需要十进制语义的计算 |
| 逻辑 | `Boolean` | `true` 或 `false` |
| 文本 | `String` | 字符串值 |
| Unicode 标量 | `CodePoint` | 单个 Unicode code point |

Norm 没有统一的 `Object` 根类型。一个值能参与哪些操作，由它的具体类型或显式实现的接口决定。

## 非空默认

```norm
String name = "Ada"
String? nickname = null
```

`String` 和 `String?` 是不同类型：

- `String` 始终包含字符串；
- `String?` 可能包含字符串，也可能是 `null`。

不能把 nullable 值直接赋给非空变量。

```norm
String? input = null
String text = input
```

第二行是不安全的，因此编译失败。程序必须先通过控制流证明 `input` 非空，或者显式处理 `null` 分支。具体收窄规则仍以[类型系统规范](/spec/type-system)为准。

```norm
if input != null {
    printLine(input.codePointSize())
}
```

`?.` 在接收者为 null 时停止成员调用链，`??` 只在左侧为 null 时求值右侧：

```norm
Integer? citySize = user.address?.city?.codePointSize()
String displayName = user.nickname ?? user.name
```

nullable 标记作用于完整类型。集合本身与集合元素的 nullability 分别表达：

```norm
List<String>? optionalNames = null
List<String?> names = ["Norm", null]
```

`null` 需要明确的 nullable 期望类型；缺少赋值目标、参数或返回类型时不能单独推断。

## 确定赋值

非空字段必须在每条构造路径上完成初始化。

```norm
class Interval {
    Integer start
    Integer end

    Interval(Integer start, Integer end) {
        this.start = start
        this.end = end
    }
}
```

Norm 不提供延迟初始化关键字。编译器通过确定赋值分析保证对象构造完成后处于有效状态。

## 数值提升

较小范围的整数可以安全提升到较大范围：

```norm
Integer count = 12
Long total = count
```

可能丢失信息的收窄转换必须显式写出目标类型：

```norm
Long total = 12
Integer count = Integer(total)
```

显式转换表示程序员接受转换语义；它不意味着运行时一定忽略越界。越界行为由正式数值规范定义。

## Decimal 与浮点类型

`Decimal` 不与 `Float` 或 `Double` 隐式混合：

```norm
Decimal exact = Decimal("0.1")
Double approximate = 0.1
```

如果计算需要跨数值模型，必须先明确转换其中一方。这避免一个表达式在没有提示的情况下改变精度规则。

## Nominal Typing

Norm 使用名义类型系统。两个类型即使具有相同字段，也不会自动互相兼容。

```norm
interface Printable {
    String printLine()
}

class Coordinate implements Printable {
    Integer x
    Integer y

    String printLine() {
        return "(${x}, ${y})"
    }
}
```

`Coordinate` 是 `Printable`，因为声明中明确写出了 `implements Printable`，而不是因为它碰巧拥有同名方法。

下一章：[Class、Value 与 Identity](/language/objects)。
