# 并发 API

`std.concurrent.Task<T>` 表示稍后完成的有类型操作，并实现 `std.io.Resource`。声明与重载以 [tasks.norm](../../norm/stdlib/std/concurrent/tasks.norm) 为准。

`std.concurrent.async` 在当前 `TaskScope` 中提交有返回值或 Void 工作；没有作用域时拒绝提交。`TaskScope.start` 由宿主实现，负责执行器与生命周期接入，标准库不依赖 UI。组件绑定该上下文后沿用 UI 队列、任务取消及清理屏障；无作用域的独立工作使用 `startTask`。标准库契约见 [AsyncExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/AsyncExecutionTest.java)。

```norm
var task = startTask { 20 }.then { result * 2 }.then { result + 2 }
var handled = task.then { printLine(result) }.error { printLine(failure.message) }
```

`startTask` 提交虚拟线程工作后立即返回；Void 工作以 Unit 表示完成。`then` 在前一阶段成功后处理结果，`error` 处理前一阶段失败，包括成功回调抛出的异常。每次注册均返回独立的后续任务，注册不会在调用栈内执行回调。返回值重载保留精确结果类型，Void 回调产生 Unit；返回另一个 Task 时保留该返回类型，不自动展开嵌套任务。

默认后续阶段在完成通知后才调度到虚拟线程，未就绪阶段不预先启动等待线程。`startTask(executor: executor) { ... }` 可指定 TaskExecutor；工作仍在虚拟线程执行，后续成功及错误阶段继承该执行器。dispatch 必须将 action 排队执行，不能内联调用或静默丢弃；拒绝调度时应抛出异常，对应阶段会失败结束。任务创建时捕获当前 ResourceOwner，后续阶段继承该归属；GUI 仍需通过执行器连接 UI 队列。任务完成、取消或关闭后解除资源归属，并从执行实例的资源列表移除；已完成结果仍可读取。关闭 owner 可取消其持有的阶段，已关闭 owner 会拒绝新的阶段。Task 不延长执行实例的生命周期；取消一个后续阶段不会取消其前置任务或兄弟阶段。

既有 `await()` 返回完成值，失败进入 Norm Exception 流程；`completed()` 观察终态，`cancel()` 请求取消。语言级行为见 [TaskExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/TaskExecutionTest.java)，调度、取消和结果转换见 [JvmJarBindingTaskTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JvmJarBindingTaskTest.java)。

指定 TaskExecutor 后，未接管结果的失败会排入该执行器，再调用其 `report(error)`；默认实现抛出异常，GUI 执行器将其交给窗口错误报告。连接后续阶段、await、取消、显式关闭或把结果交给 Java 都表示接管该任务；链末端的新失败仍可单独报告。资源自动释放不表示错误已处理。排队期间注册的处理器会抑制默认报告，已报告的错误不会因后来注册处理器而撤销。取消不作为未处理异常报告；没有指定执行器时，结果由调用者读取。

Java Binding 将 Future、CompletionStage 和 CompletableFuture 投影为同一 Task 类型。Java 来源任务使用适配后的宿主 Future 视图；Norm 新建任务提供独立转换的 Java CompletionStage 视图。长期应用入口可使用 [awaitCancellation](../../norm/stdlib/std/concurrent/lifecycle.norm) 保持执行，直到取消或中断。

Java 声明的 `Future<?>`、`CompletionStage<?>` 与 `CompletableFuture<?>` 保留未知元素类型，不投影为 `Task<Any?>`。这允许只检查完成状态的 Java API 接收不同结果类型的 Task，不改变普通参数化类型的不变性。

`termination()` 返回独立的 `Task<Unit>?` 清理完成通知：Norm 自己调度的工作退出执行体（包括 finally）后才完成，即使任务已被取消。尚未运行的工作取消或被执行器拒绝后也会完成该通知。它不继承组件的 ResourceOwner，关闭通知不会取消原工作；通知本身不代表另一个工作执行体。外部 Java Future 无法证明其后台工作已退出，因此返回 null。该 API 用于资源关闭协调；UI 线程不应调用 await 阻塞等待。
