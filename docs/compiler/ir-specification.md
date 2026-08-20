# Norm IR Specification

## Overview

Norm IR is the common intermediate representation shared by Truffle execution and future native backends.

Goals:

- preserve Norm semantic information
- support optimization
- represent value/reference semantics
- support GC runtime
- support AOT compilation

Pipeline:

Source -> AST -> Typed AST -> Norm IR -> Backend

## SSA

Norm IR uses SSA (Static Single Assignment).

Each virtual register has one assignment:

```
%1 = load user.name
%2 = call print(%1)
```

SSA enables:

- constant propagation
- dead code elimination
- escape analysis
- inlining
- control flow optimization

## Instruction Categories

### Value instructions

```
const
move
copy
convert
```

### Arithmetic

```
add
sub
mul
div
compare
```

### Memory

```
alloc
load
store
field_get
field_set
```

### Object

```
new_object
call_method
interface_call
instance_of
cast
```

### Control Flow

```
br
cond_br
return
throw
```

## Type Lowering

High level types:

```
String
User
List<T>
Result<T,E>
Ref<T>
```

are lowered into runtime representations.

Examples:

```
Ref<User>
```
becomes:

```
managed pointer + metadata
```

Value types may use:

```
inline representation
or
copy-on-write storage
```

## Exception Lowering

Norm exceptions remain structured.

Source:

```
try {
    save()
} catch Error e {
}
```

IR:

```
invoke save
success -> continue
failure -> exception block
```

Result<T,E> remains normal enum data.
