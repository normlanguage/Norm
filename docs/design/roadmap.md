# 路线图

路线图以 Norm 1.0 为目标，并遵循[实现策略决议](/design/implementation-strategy)：核心工具链使用 Java，执行后端使用 GraalVM/Truffle，CLI 使用 Native Image 发行，Zig 不进入核心实现。

已交付版本不在路线图中重复描述，统一查看 [版本记录](/versions/0.1)。

## 语言前端

固化词法、Parser、名称解析、名义类型系统、nullable、确定赋值、命名参数和控制流表达式。语法树、SemanticModel、诊断和语言服务共享同一套声明与类型信息。

## 对象与类型

完成 class identity、value、interface、继承、enum variant、模式匹配、`ref<T>` 和 reified 泛型，并建立 conformance tests。

## Bound IR 与执行

扩展唯一 Bound IR，把调用绑定、value/identity 类别、控制流边和泛型实参传给 Truffle 后端。Native Image CLI 与 JVM 执行必须保持行为一致。

## 模块与标准库

完成 package、import、项目清单和 1.0 标准库核心 API，覆盖集合、Result、I/O、时间、并发和 Java interop。

## 工具与发布

完成增量 LSP、formatter、调试与 profiling 接口、包管理器、Registry、兼容策略和发布流程。1.0 候选版冻结语言规范、诊断 code、标准库核心 API 与工具链协议。
