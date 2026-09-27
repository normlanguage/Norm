# 67 清理资源

`finally` 在 `try` 退出时执行，即使 `try` 已准备返回值也是如此。输出顺序展示先关闭再返回调用方。

<<< ../../../norm/tests/docs/tour/cleanup.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/cleanup.out{text}

动手试试：把 `return 7` 改成 `return 9`，确认清理顺序不变。

精确规则：[参考](/zh/spec/grammar/try-catch)。
