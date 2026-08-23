# 字面量

## 数字

```norm
Integer count = 42
Long population = 8_100_000_000
Double ratio = 0.125
Decimal price = Decimal("19.95")
```

下划线只能位于数字之间，用于分组且不影响值。整数默认推断为能容纳该值的标准整数类型，赋值目标可以提供更具体类型。Decimal 目前使用显式构造，避免把十进制和二进制浮点语义混淆。

## 字符串

```norm
String name = "Norm"
String line = "first\nsecond"
String message = "hello, ${name}"
```

字符串使用双引号，支持标准转义与 `${expression}` 插值。插值要求值具有明确格式化能力，不对任意对象隐式调用调试表示。

## 布尔与 Null

`true` 和 `false` 的类型是 Boolean。`null` 只能出现在已有 nullable 期望类型的位置，不能单独推断为任意类型。

## 集合

`[1, 2, 3]` 是集合构造中的元素字面量，其最终类型由构造参数或赋值目标确定。空 `[]` 没有足够信息时要求显式类型。

