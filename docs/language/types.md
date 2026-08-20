# 类型与 Null

```norm
String name = "Alice"
String? nickname = null
```

非空局部变量定义时必须初始化。Norm 不提供 `late`。

数值类型建议：

```text
byte -> short -> int -> long
float -> double
decimal
```

只允许安全 widening；narrowing 必须显式：

```norm
long total = 10
int small = int(total)
```

`decimal` 不与 `float/double` 隐式混合。Norm 没有统一 `Object` 根类型。

