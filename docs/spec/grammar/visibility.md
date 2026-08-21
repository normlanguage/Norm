# 可见性

Norm 当前只定义 `public` 与 `private` 两级可见性。没有默认包可见性，也不使用 `protected` 建立继承专用 API。

```norm
public class Account {
    private decimal balance

    public decimal currentBalance() {
        return balance
    }
}
```

## 默认规则

- 顶层类型和顶层函数默认 `public`。
- class、value 与 enum 的成员默认 `public`。
- 构造过程使用的内部字段应显式写 `private`。
- interface 成员始终属于公开契约，不能标记为 `private`。

公开声明的签名不能泄露私有类型：

```norm
private value Token { String text }
public Token scan() // 编译错误：公开签名暴露私有类型
```

## 覆盖

public 实例方法可被子类覆盖；private 方法不参与动态分派，也不能被覆盖。子类声明同名 private 方法时，它是一个新成员。

更细粒度的模块可见性仍处于设计阶段。在规则确定前，规范和示例不使用 `internal`、`friend` 或 package-private 等未定义关键字。

