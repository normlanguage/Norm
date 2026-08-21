# Annotation 与 Reflect

Annotation 为声明附加结构化元数据；`reflect` 标记代码进入运行时检查或拦截边界。两者属于进阶语言功能，不是组织普通程序的默认方式。

## 声明 Annotation

```norm
annotation Label {
    String text
}
```

Annotation 是有明确字段的元数据类型。它可以标记 class、value、函数或字段，具体允许目标由 annotation 规范定义。

```norm
@Label(text: "two-dimensional coordinate")
value Point {
    @Label(text: "horizontal position")
    int x

    @Label(text: "vertical position")
    int y
}
```

这段代码只附加元数据，不会偷偷改写 `Point` 的字段、构造方式或运行逻辑。

## 查询元数据

运行时泛型与声明元数据可以通过反射 API 查询。读取 metadata 本身不赋予修改程序结构的能力。

```norm
Class type = Point.class
```

具体查询 API 属于标准库反射模块；语言层只定义类型信息必须保留，以及访问特殊边界时必须可见。

## Reflect 边界

需要拦截函数调用或执行特殊元编程行为时，方法必须显式标记 `reflect`。

```norm
annotation Measure {
    reflect void beforeFunction(Measure annotation, Function function) {
        timer.start()
    }

    reflect void afterFunction(Measure annotation, Function function) {
        timer.stop()
    }
}
```

看到 `reflect`，读者就知道这里不再是普通函数调用语义。

## 明确限制

Norm 的反射模型不允许：

- 任意重写源代码或 AST；
- 在调用点不可见地改变类型系统；
- 生成新的隐式字段或方法；
- 把普通 annotation 自动解释为运行时拦截。

如果 metadata 只需要被工具读取，就不应使用 `reflect`。如果行为可以写成普通函数，也优先使用普通函数。

下一步可以查阅[语言规范](/spec/language-spec)，或者进入[标准库](/stdlib/overview)。

