# Norm IR Optimization Passes

## Optimization Pipeline

Norm IR 优化阶段：

```
Typed IR
 ↓
Constant Folding
 ↓
Dead Code Elimination
 ↓
Inlining
 ↓
Escape Analysis
 ↓
Lowering
```

## Escape Analysis

Norm value semantics允许编译器判断对象是否逃逸。

```norm
Point p = Point(x=1,y=2)
return p.x
```

如果 p 不逃逸，可以完全消除对象分配。

## Copy Optimization

语言保证复制语义，但实现可以：

- copy elision
- copy on write
- scalar replacement
