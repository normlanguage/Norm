---
title: Java Library Adapter
description: Java JAR 作为普通 Norm Module 内部实现的目标、边界与交付计划
---

# Java Library Adapter

## 目标

Norm Module 可以暂时使用一个 Java JAR 及其运行依赖实现公开 Norm API，也可以在后续版本移除该实现并改写为纯 Norm。模块名、导出边界、依赖方式和发布坐标不暴露实现来源。

```text
Norm API → optional JAR binding → Java dependency graph
Norm API → Norm Core
```

两条路径生成同一种 Module artifact。消费者只依赖 Module。

## 文件身份

所有 `.norm` 文件都是 Norm 源码。顶层 `Module module()` 决定模块声明；独立的 `module.norm` 是多文件模块的惯用布局，单文件应用可以让它与业务声明共存。模块声明随辅助入口经过同一套解析、类型检查、Core 降级和执行流程；JAR 声明只是该函数返回的普通 Norm 对象。生成的适配代码同样只使用公开的 Norm 语法、类型和函数。编译器携带不可由源码伪造的生成来源集合，只向这些文档开放冻结的 Binding intrinsic。内容区分负责语言与模块语义，不承担宿主权限认证。

## 模块边界

`Module module()` 是模块声明、依赖与发布配置的唯一写入口。工作目录不是依赖或发布单位，不定义 Project manifest。

一个 Module 最多包含一个可选的 `jarBinding`，其中只有一个根 JAR。根 JAR 的 POM 或本地声明可以形成传递运行依赖，但编译器只为根 JAR 中物理拥有的公开类生成可调用声明。依赖 JAR 的对象可以作为受约束的外部类型跨越签名；调用其 API 需要依赖对应的 Norm Module。

显式 `exports` 与 `jarBinding.api` 按声明顺序建立公开 Norm 名称映射。Java 类名因此不构成 Module API 身份；例如 `jakarta.persistence.EntityManager` 可以稳定导出为 `orm.Store`。省略 `exports` 时继续从 `jarType.name` 派生名称。

需要组合多个 Java 库时，每个根 JAR 分别由一个 Module 适配，再由纯 Norm Module 组合。普通 Norm 源码、生成声明和后续纯 Norm 重写共享同一个导出表。

## 声明模型

```norm
Module module() {
  return module(
    name: "commons.lang",
    version: 1,
    dependencies: [],
    binding: jarBinding(
      target: mavenJar(
        group: "org.apache.commons",
        artifact: "commons-lang3",
        version: "3.20.0",
        resolution: sha256("...")
      ),
      api: [
        jarType(
          name: "StringUtils",
          members: ["isBlank", "isNotBlank", "reverse", "split", "trim"]
        )
      ]
    )
  )
}
```

`JarType`、`JarBinding` 与构造它们的函数都是 bootstrap 中定义的普通 Norm 声明。`binding` 与 `target` 都是单值；Binding Module 的 exports 由 `api` 中的类型名派生。纯 Norm 版本以普通源码实现相同导出名。

本地 JAR 使用 `localJar(path, integrity)`。`norm resolve` 负责解析并原子填入缺失摘要；已声明摘要不匹配时直接失败，需要更新依赖的作者先修改声明。`norm run`、`norm package` 和 CI 只验证已声明内容，不接受依赖漂移。不使用独立锁文件。

## 第一版使用

本地 JAR 放在 Module 目录内，例如 `lib/tools.jar`。`jarType` 中的名字对应根 JAR 中唯一的公开类，`members` 选择构造函数、方法或字段名称，并包含该名称的稳定公开重载；构造函数使用 `new`。签名涉及的根 JAR 类型自动形成最小声明闭包。编译器为选中的 API 生成普通 Norm 声明，例如 `StringUtils.reverse` 生成 `stringUtilsReverse`。

```norm
Module module() {
  return module(
    name: "example.tools",
    version: 1,
    binding: jarBinding(
      target: localJar(path: "lib/tools.jar"),
      api: [jarType(name: "Tools", members: ["new", "convert"])]
    )
  )
}
```

首次解析将摘要写回原声明：

```text
norm resolve path/to/example/tools
norm run path/to/example/tools/Main.norm
```

Maven 根制品使用上方声明模型。另一个 Module 只声明普通 Norm 依赖并导入生成函数：

```norm
import commons.lang.stringUtilsReverse

Void main() {
  printLine(stringUtilsReverse("Norm") ?? "")
}
```

发布前先执行 `norm resolve`，再生成可直接作为 Maven 仓库目录使用的产物：

```text
norm package path/to/commons/lang --output path/to/repository
```

仓库坐标与制品名由 Module 身份派生，规则见[包管理器](/ecosystem/package-manager)。Maven 和 Gradle 都可消费生成的 NAR 与 POM。另一个 Norm 项目的 `dependency(repository, name, version?)` 使用同一坐标解析，无需 POM、Gradle 文件或锁文件。

可运行目录见 [Apache Commons Lang 示例](../examples/java-commons-lang/README.md)。

## 内容身份

路径和 Maven 坐标只负责定位。实现使用以下派生身份：

- `JarContentId`：完整 JAR 字节；
- `JavaApiId`：规范化的可绑定公开 API；
- `ResolvedJarGraphId`：制品内容与依赖边；
- `BindingArtifactId`：依赖图、映射策略和 Binding ABI；
- `ModuleApiId`：对外 Norm 声明；
- `ModuleImplementationId`：Norm Core 与可选 Binding 实现。

相同内容共享扫描和 Binding 缓存。JAR 实现变化必须重新链接；公开 Norm API 不变时，消费者源码保持有效。

## 发布模型

`norm package` 生成 NAR，以及由 `module.norm` 派生的 POM。NAR 格式版本 5 使用 ZIP 容器，所有 Module 都包含已求值的 `module.json` 和普通 Norm `sources/`，依赖项保存明确的仓库身份。纯 Norm Module 与 Java Binding Module 都保存完整生产源码；后者同时保存公开适配面生成的源码，并额外包含 `jar` manifest 与 `binding/java-api.json`。`exports` 只定义公开 API，不参与选择制品文件；示例和验证程序使用嵌套 Module 与生产 Module 隔离。Binding manifest 保存类型、成员组和精确重载公开面，API 报告在打包阶段记录完整 JAR census、结构化适配状态与 `JavaApiId`；消费端按固定 JAR 只重建公开适配面及其类型闭包，并逐个复验归档中的生成源码。NAR 不内嵌 Java JAR，也不执行远程 `module.norm`。纯 Norm 实现使用同一归档、坐标和调用边界，移除 Binding 不产生新的包种类。后续二进制 Core 复用同一容器与身份模型。

POM 声明根 Java 制品及其普通 Maven 依赖。依赖方解析 Norm Module 时同时获得所需 Java 图。发布本地 JAR 时必须为它声明可解析的发布坐标；同一次发布产生 Java artifact 和依赖它的 Norm artifact。

现有版本的 Binding artifact 不会被纯 Norm 版本原地替换。实现迁移通过同一 Module 的新版本发布。

## 强制约束

- Module 不具有 Java 专用种类；
- 每个 Module 最多绑定一个根 JAR；
- 不生成传递依赖的公开可调用 API；
- 不提供任意宿主类查找、反射调用或无类型宿主对象；
- Java 对象在 Norm 中是具有确定声明身份的不透明引用；
- 多个 Module 显式绑定同一 Java artifact 的不同版本时必须失败；传递版本由统一类路径解析器选择，显式根制品优先，其余使用 Maven 版本顺序，并只保留选中版本的依赖闭包；
- Java artifact 的固定坐标出现不同内容，以及 API 指纹不匹配时必须失败；
- 公开适配面中的未支持签名产生确定诊断；
- 远程 artifact 携带已编译模块描述，消费者不执行发布者的配置源码；
- Maven POM、摘要清单和生成声明均为派生产物；Gradle 直接消费同一 Maven 元数据。

## 当前绑定面

当前实现覆盖静态与实例方法、构造函数、静态与实例字段、基本与盒装标量、字符串、`Number`、不透明对象、Object 上界泛型和 JAR 内泛型继承投影。具体组件类型的 Java 数组映射为生成的 identity wrapper，提供固定长度、读取、原位更新和构造能力；基本类型数组与盒装类型数组保持不同名义类型，不映射为具有值语义的 Norm `Array<T>`。Java `T[]` 与 `T...` 使用按擦除组件区分的 reified 数组，可变参数调用固定为单个数组参数。

Java `Throwable`、`Exception` 与 `RuntimeException` 映射到可捕获、可回传的 Norm `Exception`；绑定调用抛出的 Throwable 进入 Norm throw/catch。实现 `AutoCloseable` 或 `java.io.Closeable` 的导出类型实现 `std.io.Resource`，由同一执行资源域负责显式关闭和退出清理。Java `Object` 映射为 `Any?`，`Path`/`File` 映射为 `std.filesystem.Path`，`URI`/`URL` 映射为 `std.http.Uri`，`CharSequence` 和 `Charset` 映射为 Norm 字符串。`java.io.InputStream` 与 `java.io.OutputStream` 映射为实现标准字节协议和资源协议的 `std.io.InputStream` 与 `std.io.OutputStream`；这些平台映射由同一类型表驱动。入口类型签名引用的根 JAR 公开类型自动进入生成闭包；显式公开的嵌套 Java 类型以完整外层类型链生成稳定顶层名，例如 `Request.Builder` 映射为 `RequestBuilder`。

根 JAR 的公开 Java interface 生成为普通 Norm interface，并保留可投影的泛型继承关系；生成的具体 class 实现对应 interface。接口方法是普通 Norm 方法，接口返回对象由私有绑定载体保持 JVM identity。该映射由统一类型关系驱动，用户源码只使用 Norm 的 interface、class 与方法调用。

公开 class 经过包私有父类继承到的公开 interface 会被还原到生成声明，泛型实参沿完整 Java 继承链代入。Java 无界通配符投影为 Norm 存在类型 `?`，因此 `Iterable<String>` 可安全传给 `Iterable<?>` 参数。

成员选择使用完整公开继承面，父类类型变量在导出 class 上完成代入；调用继续指向可公开链接的声明 owner，包私有声明则通过导出 class 链接。census 只记录真实声明，继承视图不重复写入报告。依赖 JAR 的公开类型参与继承和 SAM 识别，发布适配面仍只允许选择根 JAR 类型。

Java `Class<T>` 映射为 Norm `Class<T>?`。Binding 生成器为公开包装声明和数组包装派生 JVM descriptor；运行时用声明 identity 双向解析真实 `java.lang.Class`，返回值存在多个合法擦除视图时由调用点的 `Class<T>` 消歧。没有 Binding 映射的普通 Norm 类型不会被字符串类名或宿主反射旁路解析。

Java `java.time.Duration` 与 `std.time.Duration` 按秒和纳秒双向转换。Duration 的字段布局由标准库 ABI 生成，不由单个适配包复制；Java API 中 `<U extends T>` 形式的依赖型泛型上界保留为普通 Norm 泛型约束。

`Class<T>` 的精确实参只有在 Norm 映射保持 JVM class identity 时才进入绑定；例如会折叠宿主身份的平台 façade 和 Optional 不会伪装成另一个类令牌。raw `Class`、`Class<?>` 与由类型参数表达的类令牌继续使用运行时声明 identity。

根 JAR 中的 Java enum 生成为封闭的 Norm `enum`，公开常量成为无 payload variant。Java 静态方法生成普通函数，实例方法生成以 enum 值为首参数的普通函数；参数、返回值和 enum 数组元素在边界两侧按声明 identity 与常量 identity 双向转换。Java 标识符超出 Norm 标识符集合时，生成器保存稳定、可逆的 variant 映射。

Java `Optional<T>` 使用 Norm nullable 表达缺失，`OptionalInt`、`OptionalLong` 与 `OptionalDouble` 使用对应 nullable 标量。Java `Collection<T>`、`List<T>`、`Set<T>` 与 `Map<K,V>` 使用 `std.collections` 中的引用 class 表达共享 identity；List 与 Set 继承共同的 `MutableCollection<T>`，平台载体使用 `IterableView<T>` 与 `IteratorView<T>`。根 JAR 中实现 Java `Iterable<T>` 的类型按泛型祖先实现普通 Norm `std.core.Iterable<T>`，其 `iterator()` 返回 `std.core.Iterator<T>`，可直接用于 `for`。这些类型与值语义集合保持不同类型；双方的原位修改和重复传递的宿主 identity 均可观察。

Java 标准函数接口和根 JAR 中的公开 SAM interface 映射为 Norm 原生 `Function<R(P...)>`。标准接口的 `? super` 输入与 `? extends` 输出在投影时消解，根 JAR SAM 的泛型参数按使用点代入；Norm lambda、捕获闭包和函数引用由运行时生成真实 Java interface 实例。携带回调的 Java 调用在隔离的虚拟线程执行，回调调度器以受控的独占执行权转移保留 Java 调用线程的框架上下文；同步、异步和 Java 内部等待回调共享同一条参数、返回值与异常传播边界。

Java `Future<T>`、`CompletionStage<T>` 与 `CompletableFuture<T>` 映射为 `std.concurrent.Task<T>`。`await()` 保持元素类型并将 Java 失败送入 Norm throw/catch，`cancel()` 和 `completed()` 提供确定状态操作；Task 实现 `Resource`，显式关闭和执行域退出都会取消未完成任务。Task 传回 Java 参数时恢复原宿主对象。`java.lang.Void` 映射为 nullable `std.core.Unit`。Reactive Streams `Publisher<T>` 映射为 `std.concurrent.Publisher<T>`，订阅回调、完成、失败、取消与执行域释放沿用同一调度和资源边界。

每次打包写入的 `binding/java-api.json` 是完整声明与适配状态的机器可读 census，`module.json` 中的 `jar.api` 是发布公开面的机器可读契约。发布门禁要求公开适配面全部生成并通过行为测试。

Java Annotation 会生成普通强类型 Norm Annotation；Norm 应用上的 Annotation 在 JVM 应用边界恢复为真实 Java Annotation。需要编译期处理的 Module 将官方 JSR 269 Processor 声明为普通依赖，应用构建自动生成隔离 Java 输入并运行 Processor。生成的应用类型保留 Norm 泛型继承，并在 JVM 应用外观中提供托管实例分配入口；框架创建的实体或组件会关联回同一个 Norm 对象。入口 Module 与包含框架支持源码的纯 Norm 依赖参与处理；生成的 Binding 声明不进入应用处理面。Norm 异常和枚举值穿过 DI、事务等 Java 代理后保持原有语言语义。真实框架验收入口见 [Micronaut BBS](../examples/micronaut-bbs/README.md)。

## 验收

- 源码树不存在 `lock.norm`、手写 POM 或 Gradle 配置；
- 同一个 `module(...)` 工厂同时表达纯 Norm 和 JAR 支撑的 Module；
- 类型结构无法为单个 Module 声明两个根 JAR；
- Maven 与本地 JAR 产生相同的 Binding pipeline；
- 替换相同路径下的 JAR 会触发摘要不匹配；
- 相同 JAR 内容可以跨路径复用 Binding artifact；
- Commons Lang 的固定版本可以从 Maven 仓库解析并从 Norm 调用；
- 打包后的 Module 能在另一个项目中作为普通 Module 依赖使用，并解析其 Java 依赖；
- 移除 Binding、提供相同 Norm 导出源码后，消费端 import 和调用形态不变。
