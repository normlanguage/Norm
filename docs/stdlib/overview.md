# 标准库概览

标准库为 Norm 程序提供跨平台的基础值类型、集合、I/O 和应用能力。本节定义 1.0 标准库的模块职责与公共 API。

## 模块分层

| 层级 | 模块 | 责任 |
| --- | --- | --- |
| 核心 | core、text、collections、math、time | 不依赖外部系统的值与算法 |
| 系统 | io、filesystem、process、network、thread | 操作系统资源与并发边界 |
| 数据 | serialization、json、sql、configuration | 外部数据和持久化 |
| 安全 | crypto、security、random | 密码学与敏感值 |
| 工程 | logging、testing、command-line | 开发、诊断和应用入口 |
| 协议 | http | HTTP 客户端与服务器基础类型 |

## 共同规则

- public API 保留完整静态类型，不提供 raw collection；
- 普通值和集合遵循默认值语义，共享必须显式出现；
- 可预期失败使用 `Result<T, E>` 或 `Option<T>`；
- 文件、socket、进程等外部资源需要确定性关闭；
- 时间、编码、舍入、超时和安全策略不能依赖环境默认值；
- adapter 差异不能被不真实的统一接口掩盖。

## 集合示例

```norm
List<Integer> first = List<Integer>(values: [1, 2, 3])
List<Integer> second = first
second.add(value: 4)
// first 仍为 [1, 2, 3]
```

运行时可以使用写时复制优化，但可观察行为保持独立。确实需要共享同一集合存储位置时使用 `ref<List<Integer>>`。

## 实现策略

首版运行时可以通过 adapter 复用成熟平台的文件、网络或数据库驱动。Norm API、错误模型和资源生命周期保持稳定，未来可替换为原生实现而不改变应用源码语义。

