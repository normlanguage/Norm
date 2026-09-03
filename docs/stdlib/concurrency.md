# 并发 API

`std.concurrent` 提供跨平台的结构化任务边界。`Task<T>` 表示结果可能稍后可用的有类型操作，并实现 `std.io.Resource`。

```norm
Task<String?> task = serviceRequest()
String value = task.await() ?? ""
task.close()
```

`await()` 返回完成值，失败进入 Norm 的 Exception 流程。`completed()` 只观察完成、失败或取消状态，`cancel()` 请求取消并返回任务是否由本次请求取消。显式 `close()` 与 execution scope 清理都会取消未完成任务。

Java Binding 将 `Future<T>`、`CompletionStage<T>` 和 `CompletableFuture<T>` 投影为同一 Task 类型，传回 Java API 时保持原宿主对象。长期运行的应用入口使用 `awaitCancellation()` 持续处理跨线程回调，直到执行被取消或中断。完整声明见 [`std.concurrent.tasks`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/concurrent/tasks.norm) 与 [`std.concurrent.lifecycle`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/concurrent/lifecycle.norm)。
