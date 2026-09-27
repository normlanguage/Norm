# 40 穷尽匹配

对封闭枚举的 switch 必须覆盖每一个变体。

<<< ../../../norm/tests/docs/tour/exhaustive_matching.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/exhaustive_matching.out{text}

三个分支都存在，因此每个 State 都有结果路径。若增加新变体，也必须更新 switch；编译器会报告未覆盖情况，不会悄悄选择某个分支。

下面是单独的反例，故意遗漏 `Done`，不能运行：

<<< ../../../norm/tests/docs/diagnostics/uncovered_enum.norm{norm}

`norm check` 报告 `NORM-FLOW-0001: switch is not exhaustive`。编译器测试对这份文件验证该诊断。

动手试试：为反例补上 `Done` 分支，再执行检查。

详细规则：[参考](/zh/spec/grammar/patterns)。
