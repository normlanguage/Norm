# Norm SSA and Control Flow Specification

## Basic Blocks

Norm IR uses basic blocks as the fundamental control flow unit.

A function consists of:

- entry block
- zero or more normal blocks
- exit blocks

## Phi Nodes

SSA merge points use phi instructions.

Example source:

```norm
int value = if condition {
    break 10
} else {
    break 20
}
```

IR conceptually becomes:

```
block_true:
  v1 = const 10
  jump merge

block_false:
  v2 = const 20
  jump merge

merge:
  result = phi(v1,v2)
```

## Optimization

SSA enables:

- constant propagation
- dead code elimination
- aggressive inlining
- escape analysis
