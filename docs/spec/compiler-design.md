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
Typed IR
 ↓
Lowerer → Truffle AST
 ↓
GraalVM/Truffle 执行
```

## 前端

Lexer 负责 UTF-8、注释、标识符和字面量。Parser 只建立语法结构并恢复可报告错误，不在解析阶段猜测类型。

名称解析建立 module、import、作用域、overload set 和名义类型图。解析结果为每个名称引用绑定唯一声明或给出歧义诊断。

## 语义分析

- 类型检查和安全数值转换；
- 泛型约束求解与 use-site variance；
- overload resolution 与命名参数；
- nullable 流分析和确定赋值；
- enum switch 穷尽检查；
- control expression 路径和值类型合并；
- value/class identity 合法性验证。

## Typed IR 与 Lowering

`SemanticModel` 保存名称绑定、表达式类型和已经解析的实参到形参映射。Typed IR 固化调用目标、value/identity 类别、控制流边和泛型实参，使后端不需要重新推断前端语义。

实参 IR 保持源码顺序，同时记录目标形参槽位。Lowerer 不重新匹配命名参数；Truffle 调用节点按源码顺序求值后，再把结果写入对应形参槽位。

## GraalVM 后端

core 使用 Java 实现编译器、Language、执行节点、Interop 与 instrumentation。执行阶段只消费 Typed IR，不重新解释源码语义。

GraalVM/Truffle 是唯一官方执行后端。项目不并行维护 LLVM、Cranelift、自研机器码或 Zig 后端。Native Image 用于把 CLI 和所需运行时打包成独立原生程序，不构成第二套语言后端。

## 优化

允许复制消除、逃逸分析、写时复制、内联、常量折叠和死代码消除。优化前后必须保持：class identity、value 逻辑独立、从左到右可观察顺序和异常边界。

## 诊断与工具

编译器输出稳定错误 code、主要位置、相关声明位置和修复提示。Parser、formatter、LSP 和文档代码测试应共享 grammar/AST 定义，避免工具各自实现语言子集。

核心工具链全部使用 Java。编译器、运行时和 Truffle 后端位于 `dev.w0fv1.norm` package 族；CLI 使用 `dev.w0fv1.norm.cli`，不建立 Zig/Java FFI。

## 增量构建

模块接口摘要包含 public 签名、类型关系、enum variant 和必要 metadata。只有摘要变化才使下游重新类型检查；private 实现变化只重新编译当前模块。
