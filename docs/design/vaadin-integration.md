---
title: Vaadin 适配计划与落地方案
description: Norm 声明式响应页面、独立 Jetty 与 Spring 集成的边界和验收
---

# Vaadin 适配计划与落地方案

本文定义适配边界、实施阶段与验收条件。具体公开签名以适配模块源码为准，运行形态的支持范围以[适配工作区 README](../../../norm-vaadin/README.md)为准。

## 目标与范围

用 Norm 普通字段保存页面状态，以普通函数和闭包表达派生内容，用结果构建器组合真实 Vaadin 组件。提供两个独立运行入口：独立 Jetty、官方 Spring Boot 集成。两者共享组件 API、响应绑定与前端资源构建。

- Vaadin、Jetty 与 Spring 适配在编译器仓库之外实现，以普通 Norm Module 交付。
- 不依赖、复制或改造 JavaFX `ui` 包；桌面示例仅提供表达风格参考。
- 不为 Vaadin 新增 `std` 类型、内建类型、关键字或特殊类名识别。
- `FieldHandle` 是现有内建能力，不迁入 `std`，不再定义同名类型。双向绑定是否直接利用其已有参数上下文转换，由定向编译测试确定。
- 优先使用已有字段观察、上下文、任务和资源协议；若发现通用缺口，先单独论证其职责与必要性。
- 不调整 `micronaut.web`，不实现 Micronaut 集成。
- 不引入通用 DI 框架、虚拟 DOM 或 JavaFX 组件抽象。
- 未经明确允许不提交 Git、不发布包、不部署。

## 事实与入口

| 能力 | 唯一事实入口 |
| --- | --- |
| 内容块、普通 `if` 与循环 | [结果构建器](../spec/grammar/result-builders.md) |
| 尾随闭包、块调用链 | [函数高级规则](../spec/grammar/functions-advanced.md) |
| 属性访问与对象字段 | [Class 语法](../spec/grammar/classes.md) |
| 字段读写观察 | [fields.norm](../../norm/stdlib/std/observation/fields.norm)、[FieldObservations](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/FieldObservations.java) |
| 字段上下文捕获与写回 | [FieldHandleExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/FieldHandleExecutionTest.java) |
| 字段赋值与通知 | [FieldWriteNode](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/FieldWriteNode.java) |
| 任务调度与取消 | [tasks.norm](../../norm/stdlib/std/concurrent/tasks.norm) |
| 资源所有权 | [ownership.norm](../../norm/stdlib/std/io/ownership.norm) |
| 模块和 Java 制品 | [Java Library Adapter](java-library-adapters.md)、[包管理器](../ecosystem/package-manager.md) |
| JVM 与 Native 交付 | [应用构建](../tooling/application-build.md) |
| 应用归档中的本地模块与资源快照 | [ApplicationBundleWriterTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/build/ApplicationBundleWriterTest.java) |
| JAR 文件名与打包后模块解析 | [ResolvedJarClasspathTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/ResolvedJarClasspathTest.java)、[BundledJarGraphsTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/BundledJarGraphsTest.java) |
| JVM 资源连接与流生命周期 | [JvmJarBindingRuntimeTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JvmJarBindingRuntimeTest.java) |
| 应用与编译器的依赖隔离 | [ApplicationClassLoaderIsolationTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/ApplicationClassLoaderIsolationTest.java) |

现有观察机制不等于完整 Vaadin 反应式集成；必须验证 Norm 回调穿过 Java 边界后仍处于正确的依赖追踪与 UI 执行上下文。

### Java 类型跨包前置项

跨包签名优先引用已声明依赖提供的公开 Java 类型；没有公开所有者的外部类型保持模块内的不透明声明。页面组件与宿主的强类型传递使用同一通用机制，不能用 `Any` 或 Vaadin 专属转换规避。

类型所有者解析入口为 [ProjectJarBindingLinker](../../cli/compiler/src/main/java/dev/w0fv1/norm/project/ProjectJarBindingLinker.java)。NAR 保存公开导出的派生映射供分析使用；运行时根据固定 JAR 与模块依赖复验映射和生成源码。不得按 Java 类名合并任意 Norm 类型。

生成层入口见 [BindingPlanner](../../cli/compiler/src/main/java/dev/w0fv1/norm/jvm/BindingPlanner.java)，声明复用与改名导出的定向测试见 [JarBindingImportsTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/jvm/JarBindingImportsTest.java)。生成器测试不代替模块依赖解析和归档往返验收。

本地与归档执行、泛型、改名导出、直接与转导出依赖、菱形依赖、类型所有者歧义诊断，以及生成源码校验见 [CrossModuleJarBindingTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/project/CrossModuleJarBindingTest.java)。

## 包边界

下表是逻辑模块边界；具体 Java 根制品仍遵循一模块一个根 JAR。聚合模块只组合依赖，不重复生成公开类型。

| 模块 | 职责 | 生命周期所有者 |
| --- | --- | --- |
| `vaadin` 及组件绑定 | 组件、内容构建器、普通字段绑定、页面契约 | 页面或组件范围 |
| `jetty.server` | 独立 Servlet 容器、安装入口、监听、Session、WebSocket、关闭 | 独立服务器对象 |
| `vaadin.jetty` | Vaadin 安装、页面工厂、独立应用入口 | 委托 `jetty.server` |
| `vaadin.spring` | 官方 Spring 集成、页面创建、Bean 与作用域衔接 | Spring 应用上下文 |
| `spring.boot` | Boot 启动与关闭、配置、应用入口 | Spring 应用上下文 |

`vaadin.spring` 不依赖独立 Jetty 启动器。Spring 选择 Jetty 时通过 Boot 官方机制管理服务器。共享底层制品不意味着共享启动责任。

适配开发工作区为编译器仓库的同级 `norm-vaadin` 目录。工作区内各发布模块拥有各自的 `module.norm`；实际发布按模块所有权规则组织仓库。Java 构建配置维护桥接、宿主与应用前端制品，Norm 应用通过固定的模块依赖使用这些产物。

## 页面作者契约

可执行语法以[共享页面示例](../../../norm-vaadin/norm/dependencies/example/profile/profile.norm)为准；公开组件签名见[组件入口](../../../norm-vaadin/norm/dependencies/vaadin/components.norm)。普通字段双向输入使用现有强类型字段捕获，不能把普通字符串值当作可写地址。页面作者不需要 `State<T>`、`.value` 或 `computed`。

- 固定属性直接传值，动态属性传闭包。
- 普通函数在绑定内执行时追踪实际读取；在绑定外调用时仍是普通函数。
- 依赖标识包括对象身份与字段身份，不改变 String、Boolean 的值语义。
- 页面对象每个 UI 独立创建；不默认共享用户状态。
- 普通构建块中的 `if`、`for` 保持现有求值语义。
- 动态结构使用显式区域与按业务键维护的列表；属性变化不重建整页。
- 输入同步时机与程序写回规则明确，不重复发出业务提交事件。
- Spring 与独立宿主使用同一页面契约；依赖提供方式位于集成边界。

## 反应式实现

优先将现有字段观察连接到 Vaadin Signals，利用官方组件绑定的依赖追踪与生命周期，不在适配层另建一套通用响应引擎。

观察页面对象必须发生在执行其绑定之前。绑定执行期间，字段读取进入相应依赖节点；字段修改使节点失效；订阅按每次计算的实际读取重新建立。嵌套业务对象观察范围必须明确，不能宣称观察根对象就自动覆盖任意对象图。

一个 UI 事件可以修改多个普通字段；绑定刷新应看到事件完成后的状态。批处理只规定通知边界，不承诺对 Norm 普通字段赋值进行事务回滚。

派生计算要求只读。若要自动拒绝绑定中的写入，必须在写入前检测，不能先改变字段再抛错并宣称状态未变。现有字段通知发生在写入后，该约束需要单独验证可行实现，不用事后补丁伪装完成。

字段替换、值集合内部变更与 identity 对象内部变更分别验收。已关闭的组件范围解除字段订阅与 Java 回调；动态分支替换与列表删除必须释放旧范围。按键列表需要验证重排、同键数据更新、重复键错误以及编辑状态保持。

## 执行与资源边界

- 启动成功以端口已绑定、Vaadin 已初始化为准。
- 每个应用只有一个启动与关闭所有者。
- 服务器等待不能持有 Norm 执行锁阻塞事件回调。
- 后台任务使用现有 TaskScope 与 TaskExecutor 接入 UI 访问；不提供另一套异步语言。
- 页面关闭后任务取消或结果失效，完成回调不再更新已关闭 UI。
- 关闭顺序为停止接收、关闭页面及会话、停止容器、释放 Norm 运行域。
- 启动失败沿同一资源所有权边界清理；不吞掉初始化或关闭错误。
- 框架线程、Norm 回调和 UI 会话锁必须通过真实并发测试验证。

Spring 的关闭状态以应用上下文为唯一来源；等待需要覆盖通过 Spring 自身入口关闭上下文，并在资源销毁完成后结束。验证入口见 [BootApplicationTest](../../../norm-vaadin/spring-boot/src/test/java/dev/normlanguage/spring/BootApplicationTest.java)。

## 前端与构建

两个宿主复用官方 Vaadin 前端构建。仅使用官方标准组件且符合官方条件时可使用预编译生产 bundle；自定义主题、资源或组件需要实际构建，不能静默遗漏。

生产 bundle 归应用资源模块所有，通用组件桥接和宿主包不携带应用构建配置。共享示例的构建入口为 [profile-frontend](../../../norm-vaadin/profile-frontend/build.gradle.kts)，其产物通过普通 Norm 模块依赖进入两个宿主。

应用主题使用 Vaadin 25 的 `@StyleSheet` 入口。应用资源模块通过标准 Java 服务声明注册 `AppShellConfigurator`；Spring 自动配置据此注册应用包，Shell 的实例化和初始化仍由 Vaadin 负责。示例入口见 [ProfileShell](../../../norm-vaadin/profile-frontend/src/main/java/dev/normlanguage/example/profile/ProfileShell.java)与[服务声明](../../../norm-vaadin/profile-frontend/src/main/resources/META-INF/services/com.vaadin.flow.component.page.AppShellConfigurator)。

Norm Core 方法体并非普通 Java 方法体，Java 字节码扫描可能看不到实际组件使用。组件使用信息应由唯一的编译/适配输入派生，不让作者额外维护手工组件白名单。产物需要包含启动配置与全部浏览器资源。

应用前端使用官方完整扫描模式收集组件注解，避免按 Java 调用可达性遗漏 Norm 动态调用的组件。配置入口见应用前端构建，资源归档与双宿主验收见 [FrontendPackagingTest](../../../norm-vaadin/profile-frontend/src/test/java/dev/normlanguage/example/profile/FrontendPackagingTest.java)、[Jetty 自定义组件验收](../../../norm-vaadin/standalone/src/test/java/dev/normlanguage/vaadin/jetty/CustomComponentBrowserTest.java)和 [Spring 自定义组件验收](../../../norm-vaadin/spring/src/test/java/dev/normlanguage/vaadin/spring/CustomComponentBrowserTest.java)。

JVM、生产资源和 Native 分开验收。Spring Native 使用官方 AOT 路径；独立 Jetty Native 独立验证反射、资源、初始化和推送。某一路径未验证时如实记录，不将 JAR 可加载或服务可启动当作完整交付。

Spring 的 Bean 定义由可分析的工厂方法提供，运行时页面工厂通过应用上下文的可解析依赖传入。官方 AOT 生成、代理字节码参与编译、构建期间不创建页面，以及独立 JVM 中启用生成代码后的浏览器运行与关闭验收见 [SpringAotTest](../../../norm-vaadin/spring/src/test/java/dev/normlanguage/vaadin/spring/SpringAotTest.java)；该验收不代表 Norm Native 可运行。

AOT 生成在独立 JVM 中执行，避免运行阶段已加载的代理类影响构建输出。构建工具入口为 [VaadinSpringAotProcessor](../../../norm-vaadin/spring-aot/src/main/java/dev/normlanguage/vaadin/aot/VaadinSpringAotProcessor.java)，应用标识以 [NormSpringApplication](../../../norm-vaadin/spring/src/main/java/dev/normlanguage/vaadin/spring/NormSpringApplication.java)为准。生成类、资源与 Native 提示由[应用制品](../../../norm-vaadin/profile-spring/build.gradle.kts)携带，再通过 Norm 普通固定依赖进入构建，不向编译器核心添加 Spring 专属判断。归档内容边界见 [AotPackagingTest](../../../norm-vaadin/profile-spring/src/test/java/dev/normlanguage/example/spring/AotPackagingTest.java)。

版本选择以适配构建声明为唯一源，锁定具体制品与内容；不使用浮动版本或改写已发布制品。依赖升级需要核对 Vaadin、Servlet 环境、Spring Boot 和 Jetty 的兼容组合。

## 实施阶段与完成条件

| 阶段 | 工作 | 完成条件 |
| --- | --- | --- |
| P0 | 事实核对、包边界、依赖固定、测试入口 | 设计索引有效；无 ui 依赖与无关 std 改动；定向失败测试可运行 |
| P1 | 字段观察到官方 Signals 的桥接 | 分支依赖、对象隔离、解除订阅、事件批量刷新通过真实 Signals 测试 |
| P2 | Norm 内容构建器、属性闭包、双向输入 | 普通字段示例编译执行；嵌套函数读取、程序写回、输入事件和组件身份通过测试 |
| P3 | 独立 Jetty 包与 Vaadin 安装 | 浏览器可访问生产页面；两个会话隔离；端口冲突与正常关闭释放资源 |
| P4 | 动态区域、按键列表、页面任务 | 分支资源释放、重排保持身份、旧异步结果失效与 UI 调度通过验收 |
| P5 | Spring 基础与官方 Vaadin Spring 集成 | 同一页面逻辑在 Boot 下运行；真实 Bean/代理、作用域、启动与关闭验收通过 |
| P6 | 共享资源构建和交付 | 标准组件及自定义资源完整；独立环境启动不依赖开发服务器；Native 路径分别实测 |
| P7 | 代码审查与文档收口 | 无重复类型源、生命周期竞争、无意义 helper 或过程注释；明确支持矩阵与剩余限制 |

各阶段先编写行为测试再实现，不运行全量测试。低层使用真实 Vaadin/Jetty/Spring 对象，Norm 测试涉及真实宿主绑定，浏览器验收通过实际输入与点击完成；HTTP 200 不代替 UI 验收。

测试证据保存在对应适配模块的测试报告与浏览器验收产物中；此文不重复维护测试数量或实现逻辑。实施 Goal 持续跟踪阶段完成状态，所有必需工作完成后才标记完成。

运行形态的实测支持矩阵与验收入口统一维护在[适配工作区 README](../../../norm-vaadin/README.md)。Native 构建成功但启动失败的产物不计为运行支持。

## 官方参考

- [Vaadin 兼容性](https://vaadin.com/docs/latest/compatibility)
- [组件 Signals 绑定](https://vaadin.com/docs/latest/flow/ui-state/building-ui)
- [计算与依赖追踪](https://vaadin.com/docs/latest/flow/ui-state/effects-computed)
- [生产前端构建](https://vaadin.com/docs/latest/flow/production/production-build)
- [Vaadin Spring 集成](https://vaadin.com/docs/latest/flow/integrations/spring)
- [Vaadin Spring 作用域](https://vaadin.com/docs/latest/flow/integrations/spring/scopes)
- [Spring Boot 容器配置](https://docs.spring.io/spring-boot/how-to/webserver.html)
- [Jetty 12.1 Servlet 环境](https://jetty.org/docs/jetty/12.1/programming-guide/migration/12.0-to-12.1.html)
- [Vaadin Native 构建](https://vaadin.com/docs/latest/flow/production/native)
