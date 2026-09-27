# 81 资源所有权

在 `ResourceOwner` 上下文中创建的订阅会登记到所有者。显式 `close()` 提前释放，并从所有者的资源列表中移除；GUI 组件等宿主可以在销毁时清理仍持有的资源。

`withContext` 在回调执行期间提供所有者。返回的订阅会保留这层所有权关系，因此回调退出后再关闭，仍能解除登记。本例验证登记与提前释放，没有演示宿主销毁。

<<< ../../../norm/tests/docs/tour/resource_ownership.norm{norm}

预期输出：

<<< ../../../norm/tests/docs/tour/resource_ownership.out{text}

动手试试：再创建一个不关闭的订阅，查看所有者资源列表。

精确规则：[参考](/zh/spec/execution-context)。
