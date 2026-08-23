# Annotation 规范

Annotation 是声明上的静态元数据。它可以被编译器、构建工具或显式反射 API 读取，但不能悄悄改变普通表达式的求值方式。

## 声明

```norm
annotation Deprecated {
    String message
    String? replacement = null
}
```

参数类型限于编译期可表示的基本值、String、enum、类型描述和这些值的不可变集合。没有默认值的参数为必填参数。

## 使用

```norm
@Deprecated(message: "use parse", replacement: "parse")
Integer parseLegacy(String text) {
    return parse(text: text)
}
```

参数名必须存在，值必须满足声明类型，同一不可重复 annotation 不能在同一目标出现多次。

## 目标与保留

Annotation 声明需要指定允许目标：package、type、field、constructor、function、parameter 或 local。保留级别分为 source、binary 与 runtime。runtime 元数据才可以通过 reflect 查询。

## 限制

- Annotation 构造不执行用户函数；
- Annotation 不注入字段、方法、控制流或异常处理；
- 工具生成的代码必须能作为普通 Norm 源码或可检查 IR 查看；
- 未知编译器 annotation 是错误，未知工具 annotation 的处理由对应工具定义。

Web 路由、事务和验证不属于语言级 annotation 语义，平台默认使用显式注册 API。
