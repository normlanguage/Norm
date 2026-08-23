# 错误处理

Norm 区分两类失败：程序预期并准备处理的结果，以及打断正常执行流程的异常状态。

## 可预期失败使用 Result

解析文本可能成功，也可能得到格式错误。这两种情况都是函数正常契约的一部分。

```norm
enum ParseError {
    Empty,
    InvalidCharacter(Integer position)
}

Result<Integer, ParseError> parseInteger(String text) {
    if text.codePointSize() == 0 {
        return Err(Empty)
    }

    // 解析过程
}
```

调用者使用普通 `switch` 处理结果：

```norm
String message = switch parseInteger("42") {
    case Ok(Integer value) {
        break "value = ${value}"
    }
    case Err(Empty) {
        break "input is empty"
    }
    case Err(InvalidCharacter(Integer position)) {
        break "invalid character at ${position}"
    }
}
```

`Result<T, E>` 是普通泛型 enum。Norm 不提供类似 `?` 的自动传播语法，因为传播会引入一条不明显的函数退出路径。

## 异常用于非正常执行状态

```norm
try {
    readConfiguration()
} catch IOException error {
    printLine("cannot read configuration: ${error.message}")
} finally {
    closeResources()
}
```

Norm 保留 `try`、`catch`、`finally` 和 `throw`。异常适合无法在当前操作契约中正常表达或恢复的执行失败。

## 如何选择

| 情况 | 建议模型 |
| --- | --- |
| 输入格式可能不正确 | `Result<T, ParseError>` |
| 查找可能没有结果 | 显式 `Option<T>` 一类 enum |
| 算法存在多个正常结果 | enum |
| I/O 在执行中意外中断 | Exception |
| 违反内部不变量 | Exception |

判断标准不是“失败是否严重”，而是它是否属于函数公开、可预期的结果集合。

## 不隐藏控制流

无论使用 Result 还是 Exception，Norm 都强调退出路径在源码中可见：

- Result 必须通过 `switch` 或普通函数显式处理；
- Result 不会自动向上传播；
- 抛出异常使用 `throw`；
- 资源清理使用明确的 `finally` 或标准库资源抽象。

下一章：[Annotation 与 Reflect](/language/reflect)。
