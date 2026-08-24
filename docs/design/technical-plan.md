# 技术方案

Norm 官方实现统一使用 Java，执行后端统一使用 Truffle/GraalVM，CLI 通过 Native Image 发布为独立原生程序。完整决议见[实现策略](/design/implementation-strategy)。

## 1. Java Frontend

core 中的编译器负责 Lexer、Parser、AST、名称解析、名义类型系统、null safety、overload resolution、泛型/型变、确定赋值与 switch 穷尽检查，并输出 content-addressed Core IR。

Parser 使用手写递归下降和 Pratt 表达式解析。Syntax 与语义快照保留 SourceSpan；canonical Core 通过独立的 authoring occurrence metadata 关联源码。

## 2. GraalVM/Truffle Backend

core 同时实现 `NormLanguage`、执行节点、Interop、instrumentation、`NormClass`、`NormValue`、`NormRef` 与 reified generic metadata。

Truffle 只消费通过类型检查的 IR，不重新解析名称或推断类型。官方项目不维护第二套执行后端。

## 3. 标准库与真实应用

语言闭环完成后，依次实现 collections、text、time、I/O、JSON、HTTP、SQL、testing 和 logging。平台能力可以先使用 Java/JDK/JDBC adapter，但 public Norm API 不暴露宿主实现细节。

## 4. Native Image 发行

CLI 在开发阶段以 JVM 应用运行，正式发行通过 Native Image 生成平台原生 `norm`。发布测试同时覆盖 JVM 与 Native Image，验证诊断、退出码和程序行为一致。

## 5. 构建系统

使用 Gradle 多项目构建并锁定 Java toolchain、GraalVM 和 Truffle 版本。Zig 不进入 core、CLI 或标准库平台 adapter。

具体实施顺序见[编译器引导计划](/design/bootstrap-plan)。
