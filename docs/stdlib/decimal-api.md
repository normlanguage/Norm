# Decimal API

`Decimal` 用于十进制精度重要的计算。它不与 `Float` 或 `Double` 隐式混合，也不默认采用二进制浮点舍入规则。

## 构造

```norm
Decimal price = Decimal("19.95")
Decimal quantity = Decimal(3)
Decimal total = price * quantity
```

字符串构造保留输入的十进制值。无效文本返回解析错误的 API 形式为：

```norm
Result<Decimal, DecimalError> parsed = Decimal.parse(text: input)
```

## 舍入

任何可能丢失小数位的信息都必须提供 scale 和 rounding mode：

```norm
Decimal charged = total.round(
    scale: 2,
    mode: RoundingMode.HalfEven
)
```

支持的模式至少包括 `Down`、`Up`、`Floor`、`Ceiling`、`HalfUp` 和 `HalfEven`。除法不能得到有限十进制结果且未提供舍入规则时，操作失败而不是静默截断。

## 比较与转换

数值比较忽略表示 scale，因此 `Decimal("1.0") == Decimal("1.00")`。转换为整数必须选择截断或舍入，并检查范围；转换到 Double 是显式且可能损失精度的操作。

货币不是 Decimal 的别名。币种、舍入政策和最小单位应由独立的 `Money` 值类型表达。

