# Norm Runtime Architecture

## Runtime 目标

Norm 面向应用开发，因此采用 GC 管理内存。

## 核心组件

```
Norm Runtime
├── Memory Manager
├── GC
├── Type Metadata
├── Reflection
├── Annotation Runtime
├── Exception Runtime
└── Standard Library Runtime
```

## GC

开发者不直接管理内存。

语言提供：

- value semantics
- Ref<T>
- 自动生命周期管理

## Reflection

Norm 保留完整类型信息：

```norm
List<String>.class
```

可以获得泛型参数。
