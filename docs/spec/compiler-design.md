# 编译器架构

Norm 官方编译器使用 Java 实现，并把语法、静态规则和 GraalVM 执行后端分离。完整技术栈由[实现策略决议](/design/implementation-strategy)固定。

```text
源码
 ↓
Lexer → Token
 ↓
Parser → AST
 ↓
名称解析与声明图
 ↓
类型检查与 SemanticModel
 ↓
Binder → BoundProgram
 ↓
ExecutionBackend → Lowerer → Truffle AST
 ↓
GraalVM/Truffle 执行
```

## 前端

Lexer 负责 UTF-8、注释、标识符和字面量。Parser 只建立语法结构并恢复可报告错误，不在解析阶段猜测类型。

名称解析建立 package、import、作用域、overload set 和名义类型图。编译器先收集源码根中的全部声明签名，再分析函数体，因此跨文件解析不依赖文件顺序。解析结果为每个名称引用绑定唯一声明或给出歧义诊断。

## 语义分析

- 类型检查和安全数值转换；
- 泛型约束求解与 use-site variance；
- overload resolution 与命名参数；
- nullable 流分析和确定赋值；
- enum switch 穷尽检查；
- control expression 路径和值类型合并；
- value/class identity 合法性验证。

## 语义快照

一次项目分析产生一个不可变 `CompilationSnapshot`。快照持有唯一的项目 `SemanticModel`，并为每个文档投影 `DocumentSemanticModel`、`SpanIndex` 与 `ReferenceIndex`。诊断、补全、悬停、定义、引用和重命名只读取同一快照；文档修订变化时整体替换快照，不混用不同修订的语义结果。

未变化源码的词法和语法结果由 `CompilationEnvironment` 按文档 identity 与内容缓存。标准库预解析结果由同一环境复用，项目分析仍以一次完整声明收集和函数体检查为一致性边界。

## Bound IR 与 Lowering

`SemanticModel` 保存名称绑定、`SemanticType`、内置能力和已经解析的实参到形参映射。`Binder` 只接受通过分析的语法与语义模型，并一次性生成不可变 `BoundProgram`。Bound IR 固化声明 identity、字段 ordinal、调用目标、源码求值顺序、形参槽位、intrinsic、value transfer 和运行时泛型实参。

`TypedProgram` 只封装 `BoundProgram`。Lowerer 只消费 Bound IR，不读取 Syntax AST 或 `SemanticModel`，也不重新执行名称解析、类型推断、成员查找或命名参数匹配。Truffle 调用节点按源码顺序求值后，再把结果写入已绑定的形参槽位。

内置类型、成员签名、可迭代和索引能力由唯一 `BuiltinCatalog` 定义。前端把能力绑定为 `IntrinsicId`，后端仅按该 identity 分派，不维护第二套字符串名称表。

## GraalVM 后端

core 使用 Java 实现编译器、Language、执行节点、Interop 与 instrumentation。`ProgramRunner` 和 Polyglot Source 共用 `Compiler → BoundProgram → TruffleExecutionBackend` 执行链。输入、输出、参数和取消信号通过显式 `ExecutionContext` 传递。

guest 运行错误由 `NormGuestException` 在 Truffle 节点处携带稳定的 `RuntimeErrorCode` 和源码位置，跨公开执行边界后转换为 `NormExecutionException`。公开错误保留源码 URI、行列和 guest 调用栈，不暴露 Java 容器或算术异常。

GraalVM/Truffle 是唯一官方执行后端。项目不并行维护 LLVM、Cranelift、自研机器码或 Zig 后端。Native Image 用于把 CLI 和所需运行时打包成独立原生程序，不构成第二套语言后端。

## 优化

允许复制消除、逃逸分析、写时复制、内联、常量折叠和死代码消除。优化前后必须保持：class identity、value 逻辑独立、从左到右可观察顺序和异常边界。

## 诊断与工具

编译器输出稳定错误 code、主要位置、相关声明位置和修复提示。Parser、formatter、LSP 和文档代码测试应共享 grammar/AST 定义，避免工具各自实现语言子集。

核心工具链全部使用 Java。编译器、运行时和 Truffle 后端位于 `dev.w0fv1.norm` package 族；CLI 使用 `dev.w0fv1.norm.cli`，不建立 Zig/Java FFI。

## 增量构建

0.2 的增量边界是不可变项目快照与解析缓存。模块接口摘要和基于依赖图的下游失效属于后续版本，交付前不得以混用旧语义快照代替。
