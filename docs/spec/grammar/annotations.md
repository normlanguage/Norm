# Annotation 声明与使用

```ebnf
AnnotationDeclaration = Visibility? "annotation" Identifier
                        "targets" "(" AnnotationTarget ("," AnnotationTarget)* ")"
                        "retention" "(" Retention ")"
                        "{" AnnotationField* "}" ;
AnnotationField       = Type Identifier ("=" ConstantLiteral)? ";"? ;
AnnotationTarget      = "package" | "type" | "field" | "constructor"
                      | "function" | "parameter" | "local" ;
Retention             = "source" | "binary" | "runtime" ;
AnnotationUse         = "@" Identifier "(" NamedArgumentList? ")" ;
```

Annotation 可放在 package、enum、interface、class、value、annotation、field、constructor、function、method、parameter 和局部变量之前。具体目标必须属于 annotation 声明的 `targets` 集合。

Annotation 参数只接受命名参数和编译期字面量。字段没有默认值时，对应参数必填。
