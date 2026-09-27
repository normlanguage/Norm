# 79 字段订阅

`onChange` 在字段赋值后收到旧值和新值。`close()` 停止后续通知，但赋值本身仍会更新字段。

<<< ../../../norm/tests/docs/tour/field_subscriptions.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/field_subscriptions.out{text}

动手试试：关闭前连续两次赋值 `Done`，观察相等值过滤。

精确规则：[参考](/zh/spec/declaration-references)。
