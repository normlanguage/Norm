# Norm Generic System Formal Design

## Overview

Norm 泛型设计目标是在保持静态强类型的同时，提供运行时完整类型信息。

与 Java type erasure 不同：

```norm
List<String>.class
```
必须保留 String 参数信息。

## Generic Declaration

```norm
class Box<T> {
    T value
}
```

T 是类型变量，在编译阶段参与检查，在运行时通过 metadata 保留。

## Variance

Norm 支持 Java 风格 use-site variance：

```norm
List<? extends Person>
List<? super Employee>
```

默认泛型是不变的。

## Runtime

每个 GenericType 保存：

- raw type
- arguments
- constraints
- variance information
