# 生态路线图

生态建设服从[实现策略决议](/design/implementation-strategy)和[编译器引导计划](/design/bootstrap-plan)，不单独引入第二套编译器或执行后端。

## 第一阶段：语言闭环

- Java 编译器前端与类型检查；
- Truffle 执行后端；
- 核心运行时与最小标准库；
- 发布自带 Java runtime 的 `norm` CLI。

## 第二阶段：开发工具

- 格式化器与语言服务器；
- 包管理器与包注册表；
- 测试、诊断和性能分析工具。

## 第三阶段：应用生态

- Web 平台与数据库 adapter；
- 可观测性、部署和框架集成；
- 由稳定语言规范支撑的第三方包生态。

第三方可以研究其他实现技术，但 Norm 项目不把 LLVM、Cranelift、自研 native backend 或 Zig 工具链列入官方路线图。
