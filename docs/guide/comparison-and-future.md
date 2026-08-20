# Norm 与其他语言的区别

## Norm 与 Java

Norm 保留 Java 的工程化优点：

- 类型前置
- class/interface
- package
- exception
- annotation

但改变：

Java:
```java
User b = a;
```

通常表示引用复制。

Norm:
```norm
User b = a
```

默认表示值复制。

共享必须：

```norm
Ref<User> b = a.ref()
```

## Norm 与 Kotlin

Kotlin 提供大量现代语法，但语言概念持续增加。

Norm 选择：

- 更少关键字
- 更稳定规则
- 更少特殊情况

## Norm 与 Rust

Norm 借鉴：

- 强类型
- Result
- enum
- 显式资源模型

但不采用 ownership/borrow checker。

## Norm 与 Go

Norm 同样关注：

- 简单部署
- 高效服务端开发

但提供更强的：

- 类型系统
- 泛型
- 对象模型
- 反射模型

## Norm 与 TypeScript

Norm 不采用结构类型和复杂类型级编程。

Norm 使用 nominal typing：

类型关系必须明确声明。

# 未来方向

Norm 的发展分阶段：

## 第一阶段

完成：

- 语言规范
- 编译器原型
- 解释执行
- 标准库基础

## 第二阶段

完善：

- Web 平台
- 数据库支持
- 工程工具链

## 第三阶段

发展：

- 原生编译
- 更完整生态
- 企业级应用平台

Norm 希望成为一种长期稳定的应用开发语言。
