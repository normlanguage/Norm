# 技术方案

Norm 官方实现统一使用 Java，执行后端统一使用 Truffle，CLI 以自带 Java runtime 的平台发行包交付。完整决议见[实现策略](/design/implementation-strategy)。

## 1. Java Frontend

core 中的编译器负责 Lexer、Parser、AST、名称解析、名义类型系统、null safety、overload resolution、泛型/型变、确定赋值与 switch 穷尽检查，并输出 content-addressed Core IR。

Parser 使用手写递归下降和 Pratt 表达式解析。Syntax 与语义快照保留 SourceSpan；canonical Core 通过独立的 authoring occurrence metadata 关联源码。

## 2. Truffle Backend

`compiler` 的 `truffle` package 实现 Norm language、执行节点、Interop、instrumentation、运行时值表示与 reified generic metadata。

Truffle 只消费通过类型检查的 IR，不重新解析名称或推断类型。官方项目不维护第二套执行后端。

## 3. 标准库与真实应用

语言闭环完成后，依次实现 collections、text、time、I/O、JSON、HTTP、SQL、testing 和 logging。平台能力可以先使用 Java/JDK/JDBC adapter，但 public Norm API 不暴露宿主实现细节。系统能力、异常转换、宿主值和资源生命周期统一遵循[系统运行时架构](/design/system-runtime)。

## 4. 自包含发行

CLI 在开发与发行阶段使用同一 JVM 应用。正式发行通过 `jlink` 生成平台 runtime，并与 compiler、依赖和 launcher 一起打包。发布测试验证普通程序、动态 Java binding、Annotation Processor、诊断、退出码和 LSP 行为。

## 5. 构建系统

使用单一 Gradle 编译器模块并锁定 Java toolchain 和 Truffle 版本。Zig 不进入编译器或标准库平台 adapter。

具体实施顺序见[编译器引导计划](/design/bootstrap-plan)。
