# 09 错误与异常

公开、可预期的失败属于结果类型；打断正常执行的失败使用 Exception。

<<< ../../norm/tests/docs/tour/09_errors.norm{norm}

输出：

```text
empty input
```

## 可预期失败

业务结果使用普通数据 enum 表达，并由穷尽 switch 处理。标准库的 `Result<T, E>` 遵守同样的构造和模式规则；没有业务值的成功结果使用 `Result<Unit, E>`。

Norm 没有自动传播 Result 的特殊运算符。函数从何处退出仍由普通 `return`、`switch` 和调用明确表达。

## Exception

异常控制流使用 `try`、`catch`、`finally` 和 `throw`：

```norm
try {
  readConfiguration()
} catch IOException error {
  printLine(error.message)
} finally {
  closeResources()
}
```

查找缺失通常使用 nullable，有限的正常结果使用 enum，I/O 中断或内部不变量失败使用类型化 Exception。标准库页面会为每个边界列出具体失败类型。

异常选择与 finally 完成规则见[错误模型](/spec/error-model)和[Try/Catch 参考](/spec/grammar/try-catch)。

上一章：[Lambda 与 Extension](/learn/lambdas-extensions)。下一章：[引用](/learn/references)。
