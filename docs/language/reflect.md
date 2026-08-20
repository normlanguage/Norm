# Annotation 与 Reflect

```norm
annotation Entity {
    String table = ""
}
```

使用：

```norm
@Entity(table = "users")
class User { }
```

Annotation 可以查询被自身标记的 class/function/field，也可以包含普通方法。

需要运行时拦截或元编程时必须显式 `reflect`：

```norm
annotation Transactional {
    reflect void beforeFunction(Transactional annotation, Function function) {
        transaction.begin()
    }
    reflect void afterFunction(Transactional annotation, Function function) {
        transaction.commit()
    }
}
```

Norm 不提供宏，也不允许 reflect 任意重写 AST 或机器码。
