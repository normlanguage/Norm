# Testing API

`std.testing` 提供 `equal`、`notEqual`、`isTrue` 与 `isFalse` 四个返回 Boolean 的判定函数，用于标准库和验收程序。它们不伪装成断言，也不负责测试发现。以下 suite、上下文和失败报告是后续版本 API 设计。

仓库的可执行规格由 Norm 程序直接声明。`printLine(value)` 产生实际输出，`expectedOutputLine(value)` 在同一次 `main()` 执行中登记对应的期望行。单文件案例按测试目录发现；多文件案例由 `module.norm` 确定模块边界，并要求模块中只有一个 `main()` 入口。测试协议的实现入口是 [`NormTestKit`](https://github.com/w0fv1/norm/blob/main/tool/core/src/test/java/dev/w0fv1/norm/testing/NormTestKit.java)。

测试库使用普通顶层函数和显式 suite 注册，不要求测试类或反射 annotation。

```norm
Void parsesPositiveInteger(TestContext test) {
    Result<Integer, ParseError> result = parseInteger(text: "42")
    test.equal(actual: result, expected: Ok(42))
}

TestSuite suite() {
    return TestSuite(name: "integer parser")
        .test(name: "positive integer", body: parsesPositiveInteger)
}
```

## 断言

基础断言包括 `equal`、`notEqual`、`true`、`false`、`contains`、`matches` 和 `throws<E>`。失败信息同时展示期望值、实际值和源码位置；值的 diff 由类型化 formatter 提供。

## 隔离与清理

每个测试获得独立 `TestContext`。临时目录、随机种子和时钟等依赖通过 context 显式获取，避免测试读取全局可变状态。注册的 cleanup 按后进先出执行，即使断言失败也必须运行。

## 参数化和异步测试

参数化测试把用例数据作为普通集合提供。异步测试返回标准任务类型并由 runner 等待；超时是 suite 或单测试的显式配置。

测试发现协议、命令行输出格式和覆盖率接口仍处于草案阶段，但测试源码不应依赖编译器私有 annotation。
