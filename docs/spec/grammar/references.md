# `ref<T>` 引用语法

`ref<T>` 用于表达 value 存储位置的身份。它不负责 class 共享：class 实例本身已经具有身份。

确定的语义边界如下：

- `T` 只能是 value 类型；
- `ref<Class>` 不合法；
- 复制 `ref<T>` 后仍指向同一存储位置；
- ref 相等比较位置身份，而不是被引用值。

取地址、解引用与可空性的具体表达式形式仍需在本规范中固定。ref 相等比较位置身份，被引用值的相等比较遵循其 value 类型规则。

已确定的 value、class 和容器规则见 [Value 与 Identity 语义](/spec/value-identity-semantics)。
