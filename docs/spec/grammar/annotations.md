# Annotation Declarations and Uses

```ebnf
AnnotationDeclaration = Visibility? "annotation" Identifier
                        ImplementsClause? AggregateBody ;
AnnotationUse         = "@" Identifier ("(" AnnotationArgumentList? ")")? ;
AnnotationArgumentList = Expression ("," NamedArgument)* | NamedArgumentList ;
```

Annotation bodies share field, constructor, and method syntax with class bodies. Interfaces following `implements` declare targets and retention policy; see the [annotation specification](/spec/annotations) for the complete set of standard interfaces.

Annotations may precede packages, enums, interfaces, classes, values, annotations, fields, constructors, functions, methods, parameters, and local variables. Application arguments accept compile-time metadata values. The first argument may omit its label when the annotation declaration has a `value` parameter: `@Get("/{name}")` equals `@Get(value: "/{name}")`. All other arguments must be named.
