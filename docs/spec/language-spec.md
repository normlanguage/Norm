# Norm Language Specification

## 目标

本章节定义 Norm 的正式语言规则。Norm 不是 Java 的替代语法，而是一门拥有独立语义模型的应用开发语言。

设计目标：

- 静态强类型
- 非空默认
- 显式共享状态
- 可预测执行模型
- 适合大型业务软件
- 支持解释执行和原生编译

## 基本原则

### 显式优于隐式

Norm 避免隐藏行为：

- 不支持宏
- 不支持操作符重载
- 不支持隐式字符串转换
- 不支持隐式 nullable
- 不支持隐式 Result 传播

### 类型优先

Norm 使用类型前置：

```norm
String name = "Alice"
int age = 20
```

类型永远出现在声明位置，便于阅读和静态分析。

## 类型系统

Norm 使用 nominal typing。

类型关系由声明决定：

```norm
class User implements Serializable
```

而不是根据结构自动匹配。

## Null Safety

普通类型不能为空：

```norm
String name = "Alice"
```

nullable 类型：

```norm
String? name = null
```

编译器通过静态分析保证 non-null 类型不会出现 null。

## 值模型

Norm 有三种核心数据模型：

### class

用于具有行为和继承的数据对象。

### value

用于纯数据值。

### Ref&lt;T&gt;

用于显式共享 identity。

共享状态必须被代码直接表达。

