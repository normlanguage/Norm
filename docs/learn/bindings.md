# 02 值与绑定

变量把一个明确的静态类型绑定到名称；`var` 只省略可以从初始化器唯一确定的类型。

<<< ../../norm/tests/docs/tour/02_bindings.norm{norm}

输出：

```text
ready
2
```

## 显式类型与 `var`

```norm
Integer count = 1
String name = "Norm"
var ready = true
```

局部变量必须在声明时初始化。`var` 不代表动态类型，后续赋值仍必须符合已经推断出的类型。

下面的声明会被拒绝：

```norm
Integer count
var missing = null
var values = []
```

第一项没有初始化器；后两项缺少能够唯一决定类型的上下文。

## 基本类型

常用内建类型包括 `Integer`、`Long`、`Float`、`Double`、`Number`、`Boolean`、`CodePoint`、`String` 和 `Void`。Norm 没有统一的 `Object` 根类型，也不会把数字或字符串隐式当作 Boolean。

标识符使用 Unicode，并以 NFC 形式参与名称比较。`value`、`annotation` 和 `extension` 只在对应声明位置作为上下文关键字。

类型和字面量的精确规则见[类型系统](/spec/type-system)与[字面量](/spec/grammar/literals)。

上一章：[Hello, Norm](/learn/hello)。下一章：[函数与调用](/learn/functions)。
