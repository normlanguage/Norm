# 比较、取舍与发展方向

Norm 不是把现有语言的功能取并集。它选择熟悉的静态类型应用模型，再对 value/identity、控制流产值、运行时泛型和框架扩展做出不同约束。

比较语言最有用的方式不是统计关键字，而是看同一个工程问题由谁承担复杂度。

## 关键维度

| 维度 | Norm | Java | Kotlin | Rust | Go | TypeScript |
| --- | --- | --- | --- | --- | --- | --- |
| Null | 非空默认，`T?` 显式允许 | 普通引用可为 null，依赖 Annotation 增强 | 非空默认，平台类型存在边界 | `Option<T>` | 指针、interface 等可为 nil | `strictNullChecks` 下由 union 表达 |
| 数据模型 | `value`、`class`、`ref<T>` 分工 | class/record/primitive 多套规则 | class/data class/value class | move/copy/reference 与 ownership | value、pointer、interface | 对象引用与结构类型 |
| 泛型运行时 | Reified，精确实参进入 Core/runtime | 类型擦除 | 普通泛型擦除，inline reified 是局部能力 | 单态化 | 编译期实例化 | 运行时完全擦除 |
| 控制流产值 | `break value` 显式交出结果 | 大部分控制流是语句 | block 最后表达式产值 | block 最后表达式产值 | 控制流是语句 | 条件表达式与语句分开 |
| 扩展机制 | 显式导入的静态 extension；强类型 Annotation 协议 | Annotation、反射、processor、agent | extension、Annotation、compiler plugin | trait、macro | interface、代码生成 | decorator、类型声明合并、转换工具 |
| 失败模型 | nullable、Result、Exception 分工 | Optional/返回值/Exception | nullable/Result 模式/Exception | Option/Result，panic | 多返回值 error，panic | union/Promise rejection/Exception |
| 部署 | GraalVM Native Image 独立 CLI | JVM/JAR 或 native 工具 | JVM/native 多后端 | 原生二进制 | 原生二进制 | JavaScript runtime 或 bundle |

表格描述的是默认模型，不代表其他语言不能通过库或规范获得类似效果。Norm 的差异在于这些边界由语言和官方工具链共同固定。

## 与 Java：熟悉的工程外形，不同的运行时类型模型

Java 开发者会熟悉 Norm 的类型前置、package、class、interface、exception、annotation 和名义子类型。Norm 也保留 class 引用语义，而不是把每次 class 赋值解释成深复制。

主要变化是：普通类型非空，顶层函数不需要工具 class，结构数据使用 `value`，泛型实参不擦除，命名参数进入公开调用约定。Annotation 生命周期和结构反射读取 Norm Core metadata，不使用 Java reflection 作为语言模型。

Norm 目前没有 Java 的库生态、成熟构建体系和长期生产验证。它通过 JDK platform adapter 和成熟第三方库复用宿主能力，但这不等于源码级 Java 互操作已经成为稳定公共语言功能。

## 与 Kotlin：减少语法分支，扩大统一规则的覆盖面

Norm 与 Kotlin 都重视非空默认、顶层函数、数据建模和简洁调用。两者对 extension 的基本判断也相近：点号形式可以来自静态函数，不必真的修改 class。

Norm 更强调减少同一职责的多种写法。class 和 value 的 identity 规则、控制流的 `break value`、省略返回类型的 fluent class 方法、Annotation 策略 interface 都采用较少但更强约束的规则。它没有 `lateinit`、隐式 receiver DSL、操作符重载或编译器插件式语言扩展。

代价是表达空间更窄。偏爱 DSL、协程语法和丰富标准库的 Kotlin 项目，目前不能从 Norm 得到同等生态能力。

## 与 Rust：借鉴显式类型边界，不引入所有权证明

Norm 借鉴 enum payload、穷尽 switch、Result 和显式资源作用域，但目标不是替代 Rust 的系统编程能力。

Rust 通过 ownership、borrow 和 lifetime 在编译期证明内存与别名安全；Norm 使用垃圾回收，并通过 class/value/ref 的语言语义让应用开发者理解共享关系。Norm 的 `ref<T>` 是受控的 value 存储位置引用，不是一套通用借用系统。

这降低了普通应用代码的类型负担，也放弃了 Rust 在无 GC、可预测资源和底层控制方面的保证。

## 与 Go：保持部署简单，同时保留更丰富的类型语义

Norm 和 Go 都希望工具链直接、发行物简单、应用边界实用。Norm 的 Native Image CLI 同样以独立可执行文件交付。

Norm 选择名义 interface、非空类型、enum payload、异常、泛型运行时信息和 class identity；Go 选择更小的语言表面、结构 interface、显式 error 返回和更成熟的并发/网络标准库。

需要成熟服务端生态和轻量并发模型时，Go 目前明显更完整。需要把对象身份、结构值和运行时类型纳入统一静态模型时，Norm 提供的是另一种取舍。

## 与 TypeScript：把运行时保证交给语言实现

TypeScript 擅长渐进采用、结构类型、类型组合和 JavaScript 生态接入。它的类型在运行时消失，最终行为仍由 JavaScript 对象模型决定。

Norm 使用名义类型和 reified 泛型，编译器产生 canonical Core，运行时反射与 serialization 使用同一精确类型。它不提供任意结构类型、复杂 conditional type 或隐式 JavaScript coercion。

这提高了运行时与静态模型的一致性，也意味着 Norm 无法直接利用浏览器和 npm 生态。

## Norm 当前的优势

- value、class 与 ref 对共享关系给出统一且可检查的解释；
- 命名参数和显式控制流结果提高调用点与分支的可读性；
- reified generics、Core metadata、Reflect 与 serialization 形成一条完整类型链；
- Annotation metadata 与强类型 interceptor 共用对象模型，不需要宏系统；
- 编译器、LSP、Truffle 和 Native Image 使用同一语义与执行管线；
- 系统 API 使用有界流、资源作用域和领域 Exception，已能处理文件、HTTP 与结构数据格式。

## Norm 当前的限制

- 语言、标准库和工具链仍处于 1.0 之前，兼容承诺和诊断契约尚未冻结；
- 库生态、包管理、数据库、Web server、并发模型、调试器和 profiler 尚不完整；
- 自动结构序列化只覆盖显式标记的 value，不处理 class 对象图、循环引用和多态；
- Native Image 缩短了用户安装链路，但构建时间、镜像体积和动态能力仍受 GraalVM 模型约束；
- 生产性能、长时间运行行为和大规模项目增量体验需要更多真实应用验证。

这些不是文档脚注，而是评估 Norm 是否适合当前项目的一部分。

## 发展方向

已发布能力统一记录在[版本索引](/versions/)，未来工作统一维护在[项目路线图](/design/roadmap)。Guide 不复制一份阶段清单，以免计划和真实交付分叉。

判断后续工作的优先级时，Norm 会继续沿用三条标准：先补齐应用开发的基础闭环，再扩大生态；先建立统一抽象，再增加格式或平台实现；新能力必须同时进入编译器、LSP、JVM/Native 验收和版本契约。

下一篇：[语言手册](/language/overview)。
