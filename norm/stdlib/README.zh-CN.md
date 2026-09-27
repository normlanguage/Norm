# 标准库

[English](README.md)

本目录是随编译器交付的标准库源码根。`std/module.norm` 构造其 `Module` 描述符。模块 `std` 中的 `math.integer` 导出对应 `std/math/integer.norm`。

Norm 语言自身能表达的代码应放在这里。Builtin ABI 属于 `compiler`；运行时桥接、平台契约及其 JDK 实现属于 `compiler` 中对应领域的包。它们只暴露实现稳定 Norm API 所需的内部操作。

标准库不得引入 Zig，也不得通过公开 Norm API 暴露宿主 Java 类型。
