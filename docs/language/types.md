# 类型与 Null

Norm 是静态强类型语言。编译器在程序运行前确定每个表达式的类型，并拒绝不安全的隐式转换。

## 基本类型

核心数值类型包括：

| 类别 | 类型 | 用途 |
| --- | --- | --- |
| 数字父类型 | `Number` | 数值上界与异构数值存储 |
| 整数 | `Integer`、`Long` | 32 位与 64 位有符号整数 |
| 浮点数 | `Float`、`Double` | 二进制浮点计算 |
| 逻辑 | `Boolean` | `true` 或 `false` |
| 文本 | `String` | 字符串值 |
| Unicode 标量 | `CodePoint` | 单个 Unicode code point |

Number 是抽象公共父类型，值始终保留 Integer、Long、Float 或 Double 的具体运行时类型。Number 不可实例化，也不是把整数和浮点数压入同一种 64 位表示。泛型容器保持不变，但目标类型可以直接构造异构数值序列：

```norm
Number count = 10
List<Number> values = [1, 2.5, 3]
```

Norm 没有统一的 `Object` 根类型。一个值能参与哪些操作，由它的具体类型或显式实现的接口决定。

标准接口 `Stringable` 声明 `String toString()`。基础标量类型实现该接口；需要参与通用文本输出的用户类型应显式 `implements Stringable`。

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
}

Interval value = Interval(start: 0, end: 10)
```

class 实例化必须为所有字段提供构造参数。Norm 不提供延迟初始化关键字。

## 数字字面量与运算

数字字面量优先服从具体 expected type；无上下文时按范围选择 Integer、Long 或 Double。Number 只作为父类型，不直接决定字面量表示。算术与大小比较要求操作数具有相同的具体数值叶类型，跨叶转换必须显式表达。

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
        return "coordinate"
    }
}
```

`Coordinate` 是 `Printable`，因为声明中明确写出了 `implements Printable`，而不是因为它碰巧拥有同名方法。

下一章：[Class、Value 与 Identity](/language/objects)。
