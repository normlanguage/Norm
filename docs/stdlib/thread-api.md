# Thread API

线程 API 面向确实需要阻塞系统线程或与本地库互操作的代码。普通并发任务应优先使用更高层的 task 和结构化并发 API。

## 创建线程

```norm
Thread worker = Thread.start(
    name: "index-worker",
    body: Void() {
        buildIndex()
    }
)

worker.join()
```

线程 body 没有隐式捕获。需要传入状态时，应绑定方法或构造显式参数对象。未 join 的非守护线程会阻止进程正常退出。

## 共享状态

class 跨线程传递时保留对象 identity，但普通字段读写不因此获得线程安全。共享修改必须由锁、原子值或其他同步类型保护；value 容器仍按 value 规则传递。

```norm
class Counter {
    AtomicInt value

    Void increment() {
        value.increment()
    }
}

Counter counter = Counter(value: AtomicInt(0))
Thread worker = Thread.start(body: counter.increment)
worker.join()
```

`Mutex` 不可复制，锁的持有范围必须由作用域 API 表达。等待、join 和锁获取都应提供超时或取消版本。

## 失败

线程函数抛出的异常由 Thread 保存，并在 join 或显式结果读取时重新报告；运行时不能静默丢弃后台异常。具体内存可见性将在并发规范中固定。

线程启动、等待、超时、取消和同步状态错误使用 concurrent 领域 Exception，不返回 Result。
