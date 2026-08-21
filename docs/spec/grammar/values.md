# 值声明语法

`value` 声明没有 identity 的不可变数据类型。它适合坐标、范围、标识符等“内容相同即相等”的数据。

```norm
value Point {
    int x
    int y
}

Point origin = Point(x = 0, y = 0)
```

## 静态规则

- 每个字段必须是非空类型，或显式声明为 nullable。
- 所有字段必须在构造结束前初始化。
- 构造后不能对字段原地赋值。
- `value` 不能继承 class，也不能被 class 继承；它可以实现 interface。
- 相等与哈希由全部字段递归决定。

```norm
origin.x = 1 // 编译错误：value 字段不可修改
origin = Point(x = 1, y = 0) // 合法：变量绑定到一个新值
```

复制 `value` 时，语言保证结果彼此独立。编译器可以使用结构共享，只要程序无法观察到共享 identity。

## 与 Class 的边界

需要方法但不需要 identity 时仍可使用 `value`；需要内部可变状态时使用 `class`；需要多个位置共同修改同一实例时使用 `Ref<class>`。`value.ref()` 不存在，因为为纯值引入 identity 会破坏其相等和复制规则。

