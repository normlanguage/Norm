# 77 词法引用

`&count` 将可写局部位置借为 `ref<Integer>`；`*target` 读取并替换其中的值。引用只在调用期间有效，不能作为返回值逃逸。

<<< ../../../norm/tests/docs/tour/lexical_references.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/lexical_references.out{text}

动手试试：把 `increment` 的返回类型改为 `ref<Integer>` 并加入 `return target`；编译器会拒绝在返回类型使用 `ref`（`NORM-TYPE-0001`）。

精确规则：[参考](/zh/spec/grammar/references)。
