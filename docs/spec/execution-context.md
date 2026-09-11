# 执行上下文

`std.context` 提供按完整类型标识区分的词法上下文。公开入口见 [context.norm](../../norm/stdlib/std/context/context.norm)。

- `currentContext<T>()` 返回当前绑定的 `T?`，未绑定时为 `null`。
- `withContext(value:, action:)` 在 action 执行期间绑定 value，返回 action 的结果；无返回值 action 使用独立重载。
- 相同类型的内层绑定临时覆盖外层，正常返回或抛错后恢复；不同类型与不同泛型实参分别绑定。
- 绑定属于当前执行实例和调用线程，普通新建线程不自动继承。框架跨线程交付回调时必须显式恢复回调所属的上下文。
- 上下文不复制其中的对象，也不提供对象状态的线程安全保证。组件状态仍由 GUI 的 UI 线程规则管理。

运行实现复用 JDK ScopedValue，见 [ExecutionContexts](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/ExecutionContexts.java)。语言行为与线程隔离分别由 [ContextExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/ContextExecutionTest.java) 和 [ExecutionContextsTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/ExecutionContextsTest.java) 验证。

`std.concurrent.startTask` 在虚拟线程提交工作并返回 `Task<T>`，声明见 [tasks.norm](../../norm/stdlib/std/concurrent/tasks.norm)。任务使用执行实例的资源管理，工作结果保留 Norm 类型与对象身份；验证见 [TaskExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/TaskExecutionTest.java)。任务捕获当前 ResourceOwner，后续阶段继承归属；完成和取消会解除归属。UI 回调调度仍由执行器提供。

## 资源归属

宿主作用域可绑定 `std.io.ResourceOwner`，协议见 [ownership.norm](../../norm/stdlib/std/io/ownership.norm)。`own` 接管资源，`release` 移除已释放资源，`execute` 同步在所属上下文执行回调；它不要求标准库依赖 GUI，也不代替线程调度器。

字段订阅捕获创建时的资源归属，并通过所属上下文执行变化回调。提前关闭订阅会解除归属，作用域关闭会释放剩余订阅；未绑定资源归属时仍可手动管理。字段观察契约见[声明引用](declaration-references.md)。
