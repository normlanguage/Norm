# Norm GC Implementation Design

## Goals

Norm runtime 使用 GC，因为目标是应用开发。

## Heap Object

对象包含：

```
Header
 Type Metadata
 GC Metadata
 Fields
```

## Generational Strategy

初始实现推荐：

- Young generation
- Old generation
- Minor collection
- Major collection

## Ref Integration

Ref<T> 创建强引用关系，GC 根据引用图判断对象存活。
