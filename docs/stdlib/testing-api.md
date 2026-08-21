# Testing API

测试库使用普通顶层函数和显式 suite 注册，不要求测试类或反射 annotation。

```norm
void parsesPositiveInteger(TestContext test) {
    Result<int, ParseError> result = parseInteger(text = "42")
    test.equal(actual = result, expected = Ok(42))
}

TestSuite suite() {
    return TestSuite(name = "integer parser")
        .test(name = "positive integer", body = parsesPositiveInteger)
}
```

## 断言

基础断言包括 `equal`、`notEqual`、`true`、`false`、`contains`、`matches` 和 `throws<E>`。失败信息同时展示期望值、实际值和源码位置；值的 diff 由类型化 formatter 提供。

## 隔离与清理

每个测试获得独立 `TestContext`。临时目录、随机种子和时钟等依赖通过 context 显式获取，避免测试读取全局可变状态。注册的 cleanup 按后进先出执行，即使断言失败也必须运行。

## 参数化和异步测试

参数化测试把用例数据作为普通集合提供。异步测试返回标准任务类型并由 runner 等待；超时是 suite 或单测试的显式配置。

测试发现协议、命令行输出格式和覆盖率接口仍处于草案阶段，但测试源码不应依赖编译器私有 annotation。

