# 39 Switch 返回值

`switch` 可以在拆解枚举负载时产生返回值。

<<< ../../../norm/tests/docs/tour/switch_results.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/switch_results.out{text}

每个 `case` 绑定匹配的负载，并用 `break` 后面的字符串产生结果。函数返回这个 switch 结果；作为语句使用时则不要求结果值。

动手试试：让 Success 分支在数量前加上说明文字。

详细规则：[参考](/zh/spec/grammar/switch)。
