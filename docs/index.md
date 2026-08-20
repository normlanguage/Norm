---
layout: home
hero:
  name: Norm
  text: 普通、明确、可预测
  tagline: 面向应用层软件的静态强类型编程语言。保留成熟语法习惯，拒绝不必要的语言魔法。
  actions:
    - theme: brand
      text: 阅读语言手册
      link: /guide/introduction
    - theme: alt
      text: 查看语法
      link: /language/overview
features:
  - title: 强类型，非空默认
    details: 静态强类型、Nominal Typing、T? 显式 nullable。
  - title: 值语义默认
    details: class 与容器默认递归值复制；共享 identity 使用 Ref&lt;T&gt;。
  - title: 低魔法
    details: 无宏、无操作符重载、无任意闭包捕获；元编程必须显式 reflect。
  - title: 原生部署目标
    details: 初期 GraalVM/Truffle，长期独立 Native Backend。
---

```norm
@Entity(table = "users")
class User {
    long id
    String name
    String? email
}

User admin = for User user : users {
    if user.admin {
        break user
    }
} else {
    break defaultAdmin
}
```

Norm 当前处于**预设计阶段**。仓库先固定语言哲学、语义和实现边界，再进入编译器实现。

