# Annotation 声明与使用

```ebnf
AnnotationDeclaration = Visibility? "annotation" Identifier
                        ImplementsClause? AggregateBody ;
AnnotationUse         = "@" Identifier "(" AnnotationArgumentList? ")" ;
AnnotationArgumentList = Expression ("," NamedArgument)* | NamedArgumentList ;
```

Annotation body 与 class body 共用字段、构造器和方法语法。`implements` 后列出目标与保留策略 interface；完整标准 interface 集合见 [Annotation 规范](/spec/annotations)。

Annotation 可放在 package、enum、interface、class、value、annotation、field、constructor、function、method、parameter和局部变量之前。应用参数接受编译期元数据值。第一个实参在 Annotation 声明包含 `value` 参数时可以省略标签，`@Get("/{name}")` 等价于 `@Get(value: "/{name}")`；其他实参必须命名。
