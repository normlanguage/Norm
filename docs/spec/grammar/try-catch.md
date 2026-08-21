# Try、Catch 与 Throw

异常表示无法作为函数正常结果继续处理的执行失败。可预期失败优先使用 `Result<T, E>`，异常机制不负责自动包装 Result。

```norm
try {
    loadConfiguration()
} catch IOException error {
    print(error.message)
} finally {
    closeResources()
}
```

## Catch 选择

catch 按源码顺序匹配异常的动态类型。更具体的类型必须写在更一般的类型之前；被前一个分支完全覆盖的 catch 是编译错误。

```norm
try {
    readFile()
} catch FileNotFound error {
    print("missing: ${error.path}")
} catch IOException error {
    print(error.message)
}
```

## Finally

`finally` 在 try 正常结束、return 或 throw 后都执行。如果 finally 自身抛出异常，新异常会替代原来的完成结果，因此不应在 finally 中执行复杂业务逻辑。

## Throw

`throw expression` 要求表达式实现异常接口。Norm 当前不声明 checked exception；函数签名不列出 throws 集合。库仍应在文档中说明可能抛出的异常。

资源类型应优先提供标准库的作用域清理抽象；在该 API 定稿前，规范示例使用显式 `try/finally`。

