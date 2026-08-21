# 模式匹配

模式只出现在 switch case 等明确的匹配位置，用于测试形状并绑定局部名称。它不是任意布尔表达式。

## Variant 模式

```norm
case Number(double value) {
    print("${value}")
}
```

variant 名必须属于被匹配 enum。参数数量、顺序和类型必须与 variant 声明一致；绑定名称只在当前 case 块内可见。

## 类型模式

```norm
case Circle circle {
    print("radius = ${circle.radius}")
}
```

类型模式检查运行时名义类型并绑定收窄后的值。被前序 case 完全覆盖的类型模式不可达。

## 常量与兜底

```norm
case 0 { print("zero") }
case else { print("other") }
```

常量必须能在编译期求值，并与被匹配表达式类型兼容。`case else` 不绑定值且必须最后出现。

## 当前边界

首版不定义 guard、or-pattern、嵌套属性 pattern 或用户自定义匹配协议。复杂条件应写在 case 块中，或先转换成 enum 再穷尽匹配。
