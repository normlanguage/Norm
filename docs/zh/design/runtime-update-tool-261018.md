# 运行时更新工具

运行时更新的公共契约位于 [`std.runtime`](../../../norm/stdlib/std/runtime/updates.norm) 和 [`std.runtime.events`](../../../norm/stdlib/std/runtime/events.norm)。发布结果包含声明级变更集合；事件订阅按序列返回请求、发布、拒绝、失败、待迁移和迁移完成状态，`kind` 使用稳定的首字母大写值。运行时实现位于 [`ExecutionUpdates`](../../../cli/compiler/src/main/java/dev/w0fv1/norm/execution/ExecutionUpdates.java)、[`LiveApplicationSession`](../../../cli/compiler/src/main/java/dev/w0fv1/norm/application/LiveApplicationSession.java) 和 [`RuntimeUpdateIntrinsicDispatcher`](../../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/RuntimeUpdateIntrinsicDispatcher.java)。

声明差异由 [`RuntimeChangeAnalyzer`](../../../cli/compiler/src/main/java/dev/w0fv1/norm/execution/RuntimeChangeAnalyzer.java) 基于编译产物的 authoring identity、语义形状、注解摘要和内容摘要生成。`ExecutionUpdateResult.changes` 是 Java API 的结构化结果，标准库的 `RuntimeChange` 是同一结果的 Norm 投影；事件中的 `changes` 是可直接展示的稳定摘要。

验证入口：

- [`RuntimeChangeAnalyzerTest`](../../../cli/compiler/src/test/java/dev/w0fv1/norm/execution/RuntimeChangeAnalyzerTest.java) 验证类型、构造器和函数的旧新形状；
- [`ApplicationSessionTest`](../../../cli/compiler/src/test/java/dev/w0fv1/norm/application/ApplicationSessionTest.java) 验证真实应用更新、结构化变更和事件顺序；
- [`RuntimeUpdatesTest`](../../../cli/compiler/src/test/java/dev/w0fv1/norm/stdlib/RuntimeUpdatesTest.java) 验证标准库契约在无运行时更新协调器时返回统一不可用诊断。
