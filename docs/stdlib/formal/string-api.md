# Norm String Library Formal API

String 是语言核心 value type。

## Properties

- immutable
- Unicode aware
- GC managed
- efficient sharing allowed

## Template

```norm
"Hello ${user.name}"
```

## Operations

支持：

- length
- substring
- split
- replace
- trim
- parse

不支持：

```norm
"a" + "b"
```

字符串组合统一使用模板。
