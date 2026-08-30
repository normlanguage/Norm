# 系统运行时架构

状态：**已接受**

本文定义 std.io、filesystem、network、http、time、process、regex、crypto 与 concurrent 的统一运行时边界。各模块的 public API 由对应标准库文档定义；宿主接入、异常转换和资源生命周期以本文为唯一实现设计。

## 不变量

- 系统层 public API 使用可捕获的 Norm Exception 表达失败，不使用 Result；
- HTTP status、进程退出状态、匹配失败、验证不匹配和 EOF 等协议正常状态仍是普通值；
- public API 和 public 类型不暴露 Java、Truffle 或具体 provider；
- 宿主操作只从标准库内部 intrinsic 进入，不向应用开放第二套底层 API；
- 文件、socket、HTTP body、进程和并发 scope 等外部资源确定性关闭；
- CLI、Polyglot 和测试使用同一执行能力组装入口；
- Core 只描述已经绑定的 intrinsic identity，不依赖平台 adapter。

## 分层

```text
norm/stdlib
  → stdlib-internal intrinsic ABI
  → truffle system bridge
  → platform contracts
  → JDK platform adapter
```

`norm/stdlib` 保存 public Norm 类型、函数、异常和资源封装。只有标准库源码可以解析 system intrinsic 与 opaque handle 类型。

`compiler` 的 `platform` package 保存后端无关的 `SystemPlatform` 契约和强类型平台失败。它不构造 Norm 值，也不依赖 Truffle。

`platform.jdk` 实现文件、网络、HTTP transport、时钟、进程、regex、crypto、entropy 与 scheduler。JDK 异常在这里归一化为平台契约异常。

`truffle` package 保存宿主值表示、资源 scope、intrinsic 执行和 Norm Exception 构造。`GuestValueFactory` 按 ABI 和当前 artifact metadata 构造异常值。宿主 I/O 只在 `@TruffleBoundary` 慢路径中执行。

## 执行能力

`ExecutionContext` 保存输入输出、应用参数、执行控制和一个强类型 `SystemPlatform`。平台能力使用固定组合，不提供字符串 capability registry 或全局 service locator。

```text
SystemPlatform
├─ console
├─ fileSystem
├─ network
├─ httpTransport
├─ clock
├─ processes
├─ regexEngine
├─ cryptoProvider
├─ secureEntropy
└─ scheduler
```

默认平台由 `platform.jdk` 的唯一工厂创建。测试从同一工厂派生，仅替换需要控制的能力。取消状态与 deadline 属于每次 execution 或 child task，不属于全局平台状态。

## 异常边界

Norm 系统异常使用单继承和领域 reason enum：

```text
Exception
└─ SystemException
   ├─ IOException
   │  ├─ FileException
   │  ├─ NetworkException
   │  ├─ HttpException
   │  └─ ProcessException
   ├─ TimeException
   ├─ RegexException
   ├─ CryptoException
   └─ ConcurrentException
```

`SystemException` 提供稳定 code。领域异常提供强类型 operation、reason 和必要上下文；reason 是异常 metadata，不是返回分支。异常消息不得成为机器判断依据。

平台 adapter 把预期宿主失败转换为强类型 platform exception。Truffle 的 `GuestValueFactory` 使用当前 artifact 的 nominal metadata 构造 Norm Exception value，并通过现有 `NormThrownException` 进入 guest `try/catch/finally`。未知实现缺陷和运行时不变量继续使用不可捕获的稳定 runtime error。

异常 identity、字段 ordinal 和 intrinsic 映射进入 builtin ABI，并由编译后的标准库契约测试验证，禁止依赖显示名称猜测运行时类型。

## 宿主值

运行时提供两个可复用 shape：

- `OPAQUE_VALUE`：宿主 Clock、编译后的 Regex 和密码学状态等宿主支持值；
- `OPAQUE_RESOURCE`：文件流、socket、HTTP body、进程和 task scope 等外部资源。

每个值仍携带完整 `CoreType`。不同 public 或内部 Norm 类型可以共用 shape，但不能互相替代。

Opaque value 的相等、hash 和复制遵循其 Norm value 契约。Opaque resource 是 identity，普通赋值共享 handle；public wrapper 执行浅 `copy()` 后也共享同一内部 handle 和关闭状态。

## 资源生命周期

每次 execution 创建独立 `ResourceScope`。资源打开成功后立即注册，显式关闭成功或失败后退出 active 集合。execution 在正常返回和异常路径都关闭剩余资源。

作用域清理是执行边界，不替代 public API 的确定性关闭要求。测试必须能够断言没有资源泄漏。重复 close 不重复操作宿主资源，也不改变第一次 close 的结果。

并发 child task 共享 execution platform，但持有派生的 cancellation context。Task scope 结束前必须等待、取消或传播所有 child task，资源不能越过所属 execution。

## 标准库依赖

```text
std.core
├─ std.time
├─ std.io
├─ std.regex
├─ std.concurrent ──→ std.time
├─ filesystem ──────→ std.io, std.time
├─ network ─────────→ std.io, std.time
├─ process ─────────→ std.io, filesystem, std.time
├─ crypto ──────────→ std.io
└─ http ────────────→ std.io, network, std.time, std.concurrent
```

系统 I/O public API 默认同步。结构化并发组合阻塞操作并传播取消，宿主实现可以使用 virtual thread 或异步机制，但不建立第二套 public async API。

## 基础类型

系统模块开发首先固定以下公共语义：

- `Duration`、`Instant`、`Deadline` 与显式 `Clock`；
- `Bytes`、`TextEncoding` 与不混淆 EOF 的 `ReadChunk`；
- partial read、partial write 与 `writeAll`；
- `Resource` 关闭协议和作用域使用入口；
- cancellation 与 deadline 的传播和异常；
- Exception cause 与清理失败的保留规则。

这些类型只在各自标准库源码中声明一次，其他模块引用它们，不复制相同定义。

`Bytes` 的宿主表示是连续 `byte[]` 加逻辑区间。切片共享只读存储，文件读取直接接管 adapter 返回的 owned storage；只有显式 `toArray()`、拼接和编码边界产生复制。标准库与文件系统通过 builtin ABI 中的同一个 opaque type identity 构造字节值。

文件流 adapter 使用阻塞 `FileChannel`，公开 partial read、partial write、flush、sync 和 close。宿主调用位于 `@TruffleBoundary`，平台工厂在 execution 组装时读取 working directory，Native Image 构建期不捕获进程环境。该路径不依赖运行时反射、动态 classpath 扫描或 provider service loading。

## Intrinsic 组织

Builtin ABI 是 intrinsic identity 和 runtime shape 的单一来源。Catalog、dispatcher 和 runtime value 按 core、text、collections 与 system 领域拆分，由唯一 registry 组合并验证每个 intrinsic 恰好拥有一个声明和实现。

标准库内部能力沿用 module bootstrap 的受限可见性思路，但使用统一 access policy。新增系统模块不能再向普通应用 prelude 增加双下划线 global 或领域特例。

## 验证

每个系统能力同时具备：

- adapter 单元测试，使用其下一层真实依赖；
- Truffle 测试，验证 platform exception 转换为可捕获 Norm Exception；
- `norm/tests/stdlib` 用户级程序；
- 真实临时目录、loopback socket、fixed clock 或真实 child process 测试；
- CLI 真实 `.norm` 文件执行测试；
- 发布前 JVM 与 Native Image 行为一致性验证。

依赖公共服务的外网 smoke 程序位于 `norm/tests/live`，只手动或在发布流水线中运行，不进入默认确定性测试套件。

## 实施顺序

1. 系统异常 ABI 与 `GuestValueFactory`；
2. `SystemPlatform`、JDK platform adapter 和统一 ExecutionContext 组装；
3. opaque value、opaque resource 与 `ResourceScope`；
4. stdlib-internal intrinsic access policy 和领域 registry；
5. time 与 io 基础类型；
6. filesystem 第一条真实纵向切片；
7. concurrent、network、process、http；
8. regex 与 crypto。

每一步保持编译、执行和相关测试通过，并在进入下一步前删除被新结构替代的入口。
