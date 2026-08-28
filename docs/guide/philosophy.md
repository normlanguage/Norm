# 语言哲学

Norm 的核心判断是：程序最昂贵的阶段不是第一次写出来，而是被不同的人反复阅读、修改和运行。语言设计首先要降低长期理解成本。

这不是要求所有代码都写得冗长。Norm 会省略没有语义价值的包装，但不会隐藏会改变控制流、共享关系、失败方式或运行时行为的信息。

## 语义可见性

一段代码应当让读者在局部回答下面的问题：

- 这个值能否为 null？
- 赋值后两个变量是否观察同一个对象？
- 这个控制结构从哪条路径产生结果？
- 失败是普通缺失、业务分支还是系统异常？
- 点号调用来自真实方法还是静态 extension？
- Annotation 只是 metadata，还是会执行生命周期？
- 运行时读取的是哪个精确类型？

Norm 为这些问题保留稳定的源码表示。

| 写法 | 可见含义 |
| --- | --- |
| `T?` | 这个位置允许 null |
| `value` / `class` / `ref<T>` | 结构值、对象身份或 value 存储位置 |
| `name: expression` | 实参绑定的公开形参 |
| `break expression` | 控制流结构在这里产生结果 |
| `Result<T, E>` | 调用方需要处理的业务结果分支 |
| `throw` / `catch` | 非正常执行路径跨越调用边界 |
| `extension` | 点号语法背后是静态顶层函数 |
| `T.class` | 代码取得与该类型绑定的 `Class<T>` |

## 省略包装，不省略含义

不依赖对象状态的行为可以直接写成顶层函数：

```norm
Double midpoint(Double left, Double right) {
    return (left + right) / 2.0
}
```

这里不需要 `static`、单例对象或工具 class。省略这些结构不会损失信息。

相反，多参数调用默认保留参数标签：

```norm
Double center = midpoint(left: start, right: end)
```

标签增加了字符，却让两个同类型实参的角色留在调用点。Norm 衡量简洁性的单位不是字符数，而是读者需要从别处恢复多少上下文。

## Identity 不伪装成 Value

有状态对象需要稳定身份，结构数据需要按内容理解。把两者压进同一种“对象”语义，会让复制、相等和共享在不同 API 中产生意外。

Norm 因此区分：

- `class`：可变、具有 identity，普通赋值保留同一对象；
- `value`：由字段内容定义，构造后不可变，使用结构相等；
- 内建容器：复制容器结构，class 元素的身份继续共享；
- `ref<T>`：引用 value 的存储位置，不接受 class 类型。

实现可以使用结构共享、写时复制和逃逸分析优化 value，只要程序观察到的语义不变。

## 控制流不能偷偷产值

Norm 的控制流表达式使用 `break value` 标出结果，不把代码块的最后一项升级为隐式返回。当前发布版已经实现穷尽 switch 表达式；if 与 for 的产值形式属于 1.0 规范目标。

```norm
String describe(State state) {
    return switch state {
        case Ready { break "ready" }
        case Disabled { break "disabled" }
    }
}
```

缺失 variant 会成为穷尽性错误，也不会产生隐式 null。表达式能力存在，值流仍然清楚。

## 强类型用于约束边界

Norm 采用非空默认、确定赋值、名义 interface、泛型不变性和 reified 类型参数。这些规则优先保护模块边界，而不是鼓励类型级计算。

同一判断也适用于反射和框架扩展。`Field<Owner, Value>` 保留 owner 和字段值类型，Annotation 生命周期使用 `ParameterInterceptor<T>` 与 `FieldInterceptor<T>` 约束输入；序列化根据 exact Core type 构建计划。运行时能力不应把已经得到的类型信息降级为字符串和无类型 Map。

## 失败不是一种东西

普通缺失、业务拒绝和系统故障对调用方意味着不同的控制责任。Norm 分别使用 nullable、Result 和 Exception，不提供自动 Result 传播语法，也不要求系统 API 把异常包装成 Result。

这使函数签名能够表达稳定业务分支，同时让文件、网络、协议和运行时错误沿异常边界传播，并在合适的应用层转换。

## 扩展能力必须有边界

Norm 不引入宏或运行时代码注入。Extension function 是显式导入的静态函数；Annotation 的目标、保留策略与拦截协议由普通 interface 表达；Reflect 读取编译器保留的 Core metadata。

框架可以在这些边界内组合 validation、serialization 和生命周期行为，但不能创造一套绕过语言名称解析、类型检查和调用规则的隐形程序。

## 一种语义服务所有工具

编译器、formatter、Language Server、Core builder 和 Truffle backend 不分别猜测程序含义。名称解析、调用绑定、泛型实参、字段 ordinal 和 Annotation 应用先进入统一语义链路，后续工具消费同一结果。

这条原则也解释了为什么 Native Image 不是第二套后端：它打包同一个 Truffle 实现。开发时运行、编辑器分析和最终发行不应形成三种语言。

下一篇：[设计原则](/guide/design-principles)。
