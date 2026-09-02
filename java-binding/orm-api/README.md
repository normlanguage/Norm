# ORM

`orm@1` 是 Norm ORM 的公共持久化面。当前 JVM 实现使用 Jakarta Persistence 3.2 提供真实 Annotation 与托管存储边界；应用源码只导入 `orm`，Hibernate 由独立 Provider Module 提供。

目标与验收计划见 [Norm ORM](../../.tmp/norm-orm-goal-plan.md)。
