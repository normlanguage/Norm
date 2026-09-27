# 80 集合变更通知

对集合字段的订阅能以旧值/新值快照观察原位 `List` 变更。关闭订阅只停止通知，不阻止后续列表修改。

<<< ../../../norm/tests/docs/tour/collection_changes.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/collection_changes.out{text}

动手试试：把列表替换为内容相同的新列表，观察是否触发通知。

精确规则：[参考](/zh/spec/declaration-references)。
