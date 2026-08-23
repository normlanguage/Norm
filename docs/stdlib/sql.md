# SQL API

SQL 模块定义连接池、参数化查询、Row 解码和事务边界。具体协议由数据库 driver 实现。

```norm
Result<Order?, SqlError> order = database.queryOne(
    sql: "select id, total from orders where id = :id",
    parameters: ["id" = id],
    decode: orderRowDecoder
)
```

## 参数

值必须通过参数绑定传入，不能用字符串插值拼 SQL。标识符不能使用普通值参数，需要动态表名时使用经过 allowlist 的专门构造器。

## Row 解码

RowDecoder 显式把列名和数据库类型转换为领域值。缺失列、NULL 写入非空类型、数值越界和无效时间格式分别产生可定位的 DecodeError。

## 事务

```norm
Result<Order, OrderError> result = database.transaction(
    action: saveOrder
)
```

commit、rollback 和重试规则由 transaction API 定义。业务 Result 失败是否回滚必须在 adapter 函数中明确，不由 annotation 隐式决定。连接只在回调作用域有效，不能泄漏到事务外。

首版可以使用 JDBC adapter；未来原生 driver 必须保持相同 public 契约，并文档化隔离级别、取消和错误映射差异。
