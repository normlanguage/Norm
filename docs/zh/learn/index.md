---
title: 学习 Norm
description: 每节学习一个特性，并运行对应示例
---

# 学习 Norm

每节只引入一个新特性，提供可运行程序、经过验收的输出、细致解释和一处可以动手修改的地方。可以按顺序学习，也可以直接进入需要的主题。

完整教学路径使用 [Norm 0.25](/zh/versions/0.25) 验收。已安装版本的能力请查阅[当前状态](/zh/status)和[版本索引](/zh/versions/)。

## 编写并运行程序

| 课程 | 新特性 |
| --- | --- |
| [01 程序入口](/zh/learn/hello) | 用 `main()` 启动单文件程序 |
| [02 显式类型与赋值](/zh/learn/bindings) | 声明和更新带类型的变量 |
| [03 类型推断](/zh/learn/inference) | 由初始化器确定局部变量类型 |
| [04 字符串插值](/zh/learn/interpolation) | 把表达式放进字符串 |
| [05 运算与比较](/zh/learn/operators) | 计算数值和布尔结果 |
| [06 条件执行](/zh/learn/conditionals) | 用 `if/else` 选择代码块 |

## 用函数组织计算

| 课程 | 新特性 |
| --- | --- |
| [07 函数与显式返回](/zh/learn/functions) | 为计算命名并返回结果 |
| [08 命名参数](/zh/learn/named-arguments) | 通过标签绑定实参与形参 |
| [09 默认参数](/zh/learn/default-arguments) | 省略有默认值的实参 |
| [10 同名实参简写](/zh/learn/argument-shorthand) | 复用相同的局部变量名与参数名 |
| [11 `if` 的返回值](/zh/learn/if-results) | 从任一分支得到值 |
| [12 末尾表达式返回](/zh/learn/final-expression) | 用末尾表达式作为函数结果 |

## 集合与控制流

| 课程 | 新特性 |
| --- | --- |
| [13 列表](/zh/learn/lists) | 列表保存有序元素，长度可以改变。 |
| [14 迭代](/zh/learn/iteration) | 用 `for` 遍历元素，无需自己维护索引。 |
| [15 条件循环](/zh/learn/conditional-loops) | 条件式 `for` 会在布尔条件为真时重复执行。 |
| [16 结束循环](/zh/learn/break) | 不带值的 `break` 会立即结束当前语句循环。 |
| [17 跳过本轮](/zh/learn/continue) | `continue` 跳过当前一轮的剩余语句，然后进入下一轮。 |
| [18 数组](/zh/learn/arrays) | 数组和列表一样可按索引访问，但长度固定。 |
| [19 映射](/zh/learn/maps) | 映射把指定类型的键与指定类型的值关联起来。 |
| [20 集合](/zh/learn/sets) | 集合保留不重复的元素，并可查询元素是否存在。 |
| [21 集合的值语义](/zh/learn/collection-values) | 给内置集合赋值后，新绑定得到独立的容器值。 |
| [22 集合字面量中的 `for`](/zh/learn/collection-for) | 集合字面量可以从另一个可迭代值生成元素。 |
| [23 集合字面量中的 `if`](/zh/learn/collection-if) | 集合字面量可以只在条件成立时加入一个元素。 |
| [24 集合展开](/zh/learn/spread) | `...` 会把可迭代值中的元素插入集合字面量中的一个位置。 |

## 值与实体建模

| 课程 | 新特性 |
| --- | --- |
| [25 值声明](/zh/learn/value-declarations) | `value` 把不可变字段组合成带类型的数据值。 |
| [26 值相等](/zh/learn/value-equality) | 值按内容比较，而不是按构造次数比较。 |
| [27 类声明](/zh/learn/class-declarations) | `class` 用字段和行为表示可以变化的实体。 |
| [28 类的身份](/zh/learn/identity) | 类变量可以指向同一个实体，也可以指向字段看似相同的不同实体。 |
| [29 复制类对象](/zh/learn/copying) | 需要新的一层类身份时，显式调用 `copy()`。 |
| [30 显式构造函数](/zh/learn/constructors) | 类可以用同名的显式构造函数取代隐式字段构造。 |
| [31 计算属性](/zh/learn/computed-properties) | 计算属性以字段式语法提供访问器行为。 |
| [32 链式方法](/zh/learn/fluent-methods) | 方法可以返回 `this`，让调用方继续操作同一对象。 |

## 缺失值与状态匹配

| 课程 | 新特性 |
| --- | --- |
| [33 可空值](/zh/learn/nullable-values) | 类型后的 `?` 允许该类型的值为 `null`；没有 `?` 的类型不允许。 |
| [34 控制流收窄](/zh/learn/flow-narrowing) | 空值检查可以在分支内证明可空值实际非空。 |
| [35 安全访问](/zh/learn/safe-access) | `?.` 只在接收者存在时读取成员。 |
| [36 空值回退](/zh/learn/fallback) | `??` 只在左侧为 null 时使用右侧表达式。 |
| [37 普通枚举](/zh/learn/plain-enums) | 枚举列出有限个具名选项。 |
| [38 带数据的枚举](/zh/learn/data-enums) | 枚举的不同选项可以携带不同类型的数据。 |
| [39 Switch 返回值](/zh/learn/switch-results) | `switch` 可以在拆解枚举负载时产生返回值。 |
| [40 穷尽匹配](/zh/learn/exhaustive-matching) | 对封闭枚举的 switch 必须覆盖每一个变体。 |
| [41 嵌套模式](/zh/learn/nested-patterns) | 模式可以匹配另一个变体负载中的变体。 |

## 组合抽象

| 课程 | 新特性 |
| --- | --- |
| [42 接口声明](/zh/learn/interface-declarations) | 约定可调用的行为。 |
| [43 实现接口](/zh/learn/interface-implementation) | 提供契约要求的方法。 |
| [44 接口默认方法](/zh/learn/default-implementations) | 在接口中共享行为。 |
| [45 类继承](/zh/learn/inheritance) | 扩展类并初始化基类。 |
| [46 重写方法](/zh/learn/overrides) | 派发到子类实现。 |
| [47 泛型类型](/zh/learn/generic-types) | 用类型参数定义数据类型。 |
| [48 泛型函数](/zh/learn/generic-functions) | 用类型参数定义计算。 |
| [49 泛型推断](/zh/learn/generic-inference) | 根据调用实参推断类型参数。 |
| [50 匹配泛型类型](/zh/learn/retained-generic-matching) | 在运行时区分泛型实例。 |
| [51 函数重载](/zh/learn/overloads) | 根据实参类型选择签名。 |

## 函数作为值

| 课程 | 新特性 |
| --- | --- |
| [52 函数值](/zh/learn/function-values) | 存储并传递可调用值。 |
| [53 Lambda 表达式](/zh/learn/lambdas) | 在使用处创建函数值。 |
| [54 捕获外部值](/zh/learn/capture) | 保留对外层值的访问。 |
| [55 绑定方法引用](/zh/learn/method-references) | 将可调用方法绑定到对象。 |
| [56 扩展函数](/zh/learn/extensions) | 用接收者形式调用静态函数。 |
| [57 尾随回调](/zh/learn/trailing-callbacks) | 用尾随代码块传递最后一个回调。 |
| [58 具名回调参数](/zh/learn/named-callbacks) | 为回调传入值命名。 |
| [59 串联代码块调用](/zh/learn/block-call-chains) | 用尾随代码块组合函数。 |

## 构建项目

| 课程 | 新特性 |
| --- | --- |
| [60 导入](/zh/learn/imports) | 使用其他包中的公开声明。 |
| [61 模块](/zh/learn/modules) | 描述模块名称、版本和导出。 |
| [62 包](/zh/learn/packages) | 为源文件建立结构化名称。 |
| [63 可见性](/zh/learn/visibility) | 分离公开 API 与文件内部细节。 |
| [64 本地依赖](/zh/learn/dependencies) | 通过模块关系消费打包的库。 |
| [65 测试](/zh/learn/testing) | 声明并运行测试。 |
| [66 异常处理](/zh/learn/exceptions) | 捕获异常控制流。 |
| [67 清理资源](/zh/learn/cleanup) | 在各种退出路径执行清理。 |

## 描述声明

| 课程 | 新特性 |
| --- | --- |
| [68 类型声明引用](/zh/learn/type-references) | 通过受检查的声明读取类型元数据。 |
| [69 字段声明引用](/zh/learn/field-references) | 通过字段描述符读写字段。 |
| [70 函数声明引用](/zh/learn/function-references) | 使用受检查的可调用声明引用。 |
| [71 注解目标](/zh/learn/annotation-targets) | 限制注解可应用的位置。 |
| [72 注解保留策略](/zh/learn/annotation-retention) | 选择注解数据保留到哪个阶段。 |
| [73 运行时注解元数据](/zh/learn/runtime-annotations) | 读取运行时保留的注解。 |
| [74 函数拦截](/zh/learn/function-interception) | 用 `around` 包裹函数调用。 |
| [75 拦截器的进入与退出](/zh/learn/interceptor-order) | 观察 `before`、函数体与 `after` 的顺序。 |
| [76 给 Agent 使用的声明文档](/zh/learn/document-annotation) | 通过 `@Document` 与查询提供受检查的用途说明。 |

## 观察与构建

| 课程 | 新特性 |
| --- | --- |
| [77 词法引用](/zh/learn/lexical-references) | 借用可写的局部存储位置。 |
| [78 字段句柄](/zh/learn/field-handles) | 把字段描述符绑定到对象。 |
| [79 字段订阅](/zh/learn/field-subscriptions) | 观察赋值并关闭订阅。 |
| [80 集合变更通知](/zh/learn/collection-changes) | 通过字段观察集合变更。 |
| [81 资源所有权](/zh/learn/resource-ownership) | 登记并释放被持有的订阅。 |
| [82 结果构建器](/zh/learn/result-builders) | 把多个表达式累积成结果。 |
| [83 结果构建器中的控制流](/zh/learn/builder-control-flow) | 累积选中的分支与循环迭代结果。 |

## 使用库

| 课程 | 新特性 |
| --- | --- |
| [84 JSON 值往返转换](/zh/learn/json-roundtrip) | 编码和解码受类型检查的值。 |
| [85 写入并读取文件](/zh/learn/file-write) | 认领新文件并读回内容。 |
| [86 类型化的文件错误](/zh/learn/file-failures) | 根据类别处理缺失文件。 |
| [87 使用已打包的库](/zh/learn/commons-lang) | 通过直接依赖消费外部库。 |

完整的 GUI Todo 与 Web 留言板请使用 Norm 0.25 生成的 [`norm hello` 程序](/zh/tooling/#从示例开始)。

[语言参考](/zh/spec/language-spec)定义精确规则，[当前状态](/zh/status)说明已发行能力。

从[程序入口](/zh/learn/hello)开始。
