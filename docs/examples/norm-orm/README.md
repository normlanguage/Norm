# Norm ORM

该示例只使用 `orm` 的实体 Annotation，并由 `orm.hibernate` 在 H2 上执行真实建表、事务、写入和按主键读取。

`orm.Entity`、`orm.Id` 与 `orm.Generated` 在 JVM 应用边界成为真实 Jakarta Persistence Annotation。应用实体仍是普通 Norm class，数据库生成的主键会同步回同一个 Norm 对象。

```text
norm run docs/examples/norm-orm/app/sample/orm/Main.norm
```
