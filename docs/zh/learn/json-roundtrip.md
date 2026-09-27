# 84 JSON 值往返转换

`@Serializable` 值具有受类型检查的 JSON 结构。`toJson` 编码字段，`fromJson<Note>` 解码回声明的类型，而不是留下动态数据树。

<<< ../../../norm/tests/docs/tour/json_roundtrip.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/json_roundtrip.out{text}

动手试试：把 `priority` 改成 3，比较编码和解码后的值。

精确规则：[JSON API](/zh/stdlib/json-api)。
