# 测试 API

`std.testing.Test` 标记可独立执行的顶层、无类型参数、无参数 `Void` 函数。正常返回表示通过；未处理异常或断言失败表示失败。声明与关联字段见 [`testing/tests.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/testing/tests.norm)。

```norm
package sample.math

import std.testing.Test
import std.math.clamp

@Test(functions: [clamp.function])
Void clampBelowMinimum() {
  require(condition: clamp(value: -2, minimum: 0, maximum: 10) == 0, message: "lower bound")
}
```

不需要文档关联的测试使用 `@Test`。`types`、`functions`、`fields` 指向被测声明，API 文档据此建立反向关系。关联不代表覆盖率或测试通过状态。重载引用仍需要能唯一确定目标的函数类型。

## 执行

```bash
norm test path/to/module
norm test path/to/module --filter sample.math
norm test path/to/module --filter sample.math.clampBelowMinimum
norm test path/to/test.norm
```

筛选匹配完整函数名或 package 前缀；没有匹配测试时命令失败。每个 Norm 测试使用独立执行上下文及运行资源，相对文件路径从测试源码所在目录解析。编辑器在测试声明处提供 `Run Test`，调用相同 CLI 入口。

源码集合及 package 归属见 [模块系统](/spec/module-system#source-set)。标准库测试位于 [`std/tests/test`](https://github.com/normlanguage/Norm/tree/main/norm/stdlib/std/tests/test)，属于 `std` 模块的测试源码集合。

## 断言与输出

可组合的判定函数见 [`testing/predicates.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/testing/predicates.norm)。判定函数返回 Boolean，可交给 `require` 检查。

[`testing/output.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/testing/output.norm) 提供期望输出协议。测试声明了非空期望输出时，运行器会比较本次测试的完整实际输出；没有声明期望输出时，打印仅用于观察。

测试执行通过 JUnit Platform 汇总，编译、资源准备和 Core 调用复用应用执行链路。语言入口及多模块启动验收继续使用 [`norm/tests`](https://github.com/normlanguage/Norm/tree/main/norm/tests) 中的独立程序。
