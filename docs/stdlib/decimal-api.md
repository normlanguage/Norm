# Decimal API

`decimal` 用于十进制精度重要的计算。它不与 `float` 或 `double` 隐式混合，也不默认采用二进制浮点舍入规则。

## 构造

```norm
decimal price = decimal("19.95")
decimal quantity = decimal(3)
decimal total = price * quantity
```

字符串构造保留输入的十进制值。无效文本返回解析错误的 API 形式为：

```norm
Result<decimal, DecimalError> parsed = Decimal.parse(text = input)
```

## 舍入

任何可能丢失小数位的信息都必须提供 scale 和 rounding mode：

```norm
decimal charged = total.round(
    scale = 2,
    mode = RoundingMode.HalfEven
)
```

支持的模式至少包括 `Down`、`Up`、`Floor`、`Ceiling`、`HalfUp` 和 `HalfEven`。除法不能得到有限十进制结果且未提供舍入规则时，操作失败而不是静默截断。

## 比较与转换

数值比较忽略表示 scale，因此 `decimal("1.0") == decimal("1.00")`。转换为整数必须选择截断或舍入，并检查范围；转换到 double 是显式且可能损失精度的操作。

货币不是 decimal 的别名。币种、舍入政策和最小单位应由独立的 `Money` 值类型表达。

