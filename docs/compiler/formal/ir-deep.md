# Norm Intermediate Representation

## Purpose

Norm IR is the common representation shared by:

- Truffle backend
- LLVM backend
- Cranelift backend

## SSA Form

Every value is assigned once.

Example:

```
%1 = load user.name
%2 = call validate(%1)
cond_br %2, block_ok, block_error
```

## Core Instructions

### Memory

```
alloc
load
store
field_get
field_set
```

### Calls

```
call
interface_call
virtual_call
```

### Control

```
branch
conditional_branch
return
throw
```

## Lowering

High level:

```
Ref<User>
```

becomes runtime managed reference:

```
object pointer + metadata pointer
```

The IR keeps semantic information until backend lowering.
