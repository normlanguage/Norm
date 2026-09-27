# 50 匹配泛型类型

两个 `Box` 分支在运行时区分 `Box<Integer>` 与 `Box<String>`，并让每个分支中的 `item` 具有相应类型。

<<< ../../../norm/tests/docs/tour/retained_generic_matching.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/retained_generic_matching.out{text}

动手试试：加入一个 `Box<Boolean>` 候选值，预测会进入哪个分支。

精确规则：[语言参考](/zh/spec/grammar/patterns)。
