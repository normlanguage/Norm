# 实现策略决议

状态：**已接受**
适用范围：Norm 编译器、运行时、执行后端、CLI 与核心开发工具

本文记录 Norm 项目的实现技术栈。它约束官方实现，不属于 Norm 语言的语法或类型系统；第三方实现仍可使用其他技术。

## 决议

Norm 官方实现遵循以下四条规则：

1. **Java 编写全部核心工具链。** Lexer、Parser、AST、名称解析、类型检查、content-addressed Core IR、格式化器、LSP 共享组件、包工具核心逻辑和 CLI 均以 Java 实现。
2. **Truffle 是唯一官方执行后端。** Norm 程序通过 Truffle language implementation 执行，Lowerer 只接受 canonical Core。
3. **CLI 发行物自带 Java runtime。** 官方发行物包含编译器、依赖和按平台生成的精简运行时；用户不安装 Java，也能动态加载独立发布的 Java binding 与 Annotation Processor。
4. **Zig 不进入核心实现。** core、CLI 和标准库平台 adapter 不包含 Zig 代码，也不建立 Zig/Java FFI 边界。

## 工程边界

```text
cli/                    命令行产品
  compiler/             Java 编译器、执行运行时、CLI 与 Language Server
  extensions/           编辑器扩展
norm/                   使用 Norm 编写的标准库与语言源码
```

官方 Java 实现是单一 Gradle 与 JPMS 模块。编译前端、Core、执行、项目、平台和 CLI 仍按领域 package 分离，并由架构测试固定依赖方向；物理模块不重复表达同一边界。标准库公开 API 使用 Norm 编写。具体 package 职责、依赖方向和验证要求以[工具链开发规范](/design/toolchain-development)为准。

## 构建与发行

- 使用单一 Gradle 编译器模块；
- Java toolchain 和 Truffle 版本在仓库中锁定；
- 单元测试与 Truffle 集成测试使用同一 JVM 执行模型；
- release job 使用 `jlink` 构建各平台自包含 `norm`；
- JAR 作为内部构建产物，不作为普通用户的主要安装界面。

## 不采用 Zig 核心工具链的原因

Truffle 的 language、Node、Interop 和 Context API 位于 Java 侧。如果前端使用 Zig，必须额外设计 C ABI、内存所有权和 AST/IR 序列化协议，源码位置、诊断和泛型 metadata 也需要跨语言复制。这些成本不能改善 Norm 的语言语义或首版交付速度。

Zig 可以在未来用于与核心实现无关的实验或外部工具，但不能成为官方构建、执行或发布链路的必需依赖。改变本决议需要新的项目提案，同时给出迁移成本、调试方案和生态兼容性影响。

## 非目标

- 不维护独立 native compiler backend；
- 不同时实现 Truffle AST 与另一套执行引擎；
- 不在第一阶段构建完整标准库、Web 平台或包注册表；
- 不为缩小工具体积牺牲运行时动态加载能力。

下一步见[编译器引导计划](/design/bootstrap-plan)。
