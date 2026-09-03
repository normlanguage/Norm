# 测试 API

`std.testing` 提供可组合的测试判定与批量期望输出。公共声明以 [`testing/predicates.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/testing/predicates.norm) 和 [`testing/output.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/testing/output.norm) 为准。

```norm
Boolean equal<T>(T actual, T expected)
Boolean notEqual<T>(T actual, T expected)
Boolean isTrue(Boolean actual)
Boolean isFalse(Boolean actual)
Void expectedOutputLines(Iterable<String> lines)
```

`expectedOutputLine(String)` 是测试执行协议的单行原语；批量用例导入 `std.testing.expectedOutputLines`。单文件案例按测试目录发现，多文件案例由 `module.norm` 确定边界，并且每个案例只有一个 `main()` 入口。测试协议入口是 [`NormTestKit`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/testing/NormTestKit.java)。
