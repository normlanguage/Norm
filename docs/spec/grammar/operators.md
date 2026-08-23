# 运算符

Norm 的运算符集合有限且不能由用户重载。相同符号在所有类型上保持同一类语义。

## 算术

`+`、`-`、`*`、`/`、`%` 适用于规范明确支持的数值类型。一元 `+` 与 `-` 不执行隐式类型转换。整数除法、除零和溢出行为由数值规范固定，不能随优化级别改变。

String 不使用 `+` 与任意对象隐式拼接；字符串模板负责格式化。

## 比较

`==` 和 `!=` 根据数据类别比较：value 使用结构相等，class 使用对象 identity，ref 使用存储位置 identity。`<`、`<=`、`>`、`>=` 只适用于具有语言内建顺序的数值，其他类型通过 Comparable 方法显式比较。

ref 指向值的比较必须显式读取该值，不能把位置 identity 与内容相等混为一谈。

## 逻辑

`!`、`&&`、`||` 只接受 Boolean。`&&` 和 `||` 从左到右求值并短路，不把数字、String 或 nullable 值转换为 Boolean。

## Nullable

`receiver?.member` 只在 receiver 非 null 时读取成员或执行方法调用。receiver 只求值一次，方法参数在 null 分支不求值。结果类型是成员结果的 nullable 形式；返回 Void 的 safe call 仍为 Void。

`nullable ?? fallback` 在左侧非 null 时返回左侧值，否则求值并返回 fallback。两侧从左到右求值，fallback 类型必须与左侧的非空部分兼容。

## 类型操作

`is` 检查运行时名义类型并可触发控制流收窄；`as` 执行显式转换，失败行为由类型系统规则定义。

完整优先级见[运算符优先级](/spec/grammar/operators-precedence)。
