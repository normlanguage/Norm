# Math

Math 模块提供固定语义的数值函数。它是顶层函数集合，不需要 `Math` 工具 class 或 static 方法。

当前实现已交付整数 `abs`、`min`、`max`、`clamp` 与 `sign`，实现在 `norm/stdlib/std/math`。下面其余函数属于 1.0 API 设计。

`clamp` 要求 `minimum <= maximum`。

```norm
import std.math.clamp

Integer opacity = clamp(value: input, minimum: 0, maximum: 100)
```

## 函数组

- 基础：`abs`、`min`、`max`、`clamp`、`sign`；
- 舍入：`floor`、`ceiling`、`truncate`、`round`；
- 幂与对数：`sqrt`、`pow`、`exp`、`log`；
- 三角：`sin`、`cos`、`tan` 及反函数。

三角函数以弧度为单位。角度转换通过 `degreesToRadians` 等明确函数完成。

## 特殊值

Float/Double 遵循已选定的 IEEE 754 子集，NaN、Infinity 和有符号零的比较必须在数值规范中固定。整数溢出策略不能由优化级别改变。

Decimal 使用自己的舍入 API，不自动调用二进制浮点 Math。需要统计、矩阵或任意精度算法时使用独立库，避免让核心模块无限增长。
