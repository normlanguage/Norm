# Norm IR Deep Specification

## 1. Purpose

Norm IR is the common representation between the frontend and execution backends.

Backends include:

- Truffle interpreter backend
- LLVM backend
- Cranelift backend

## 2. SSA Form

Norm IR uses Static Single Assignment.

Example:

```
a = 1
b = a + 2
```

becomes:

```
%1 = constant 1
%2 = add %1, 2
```

Every SSA value has one definition.

## 3. Core Instructions

### Constants

```
const
```

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
virtual_call
interface_call
```

### Control Flow

```
branch
conditional_branch
return
throw
```

## 4. Lowering

High level Norm concepts are preserved until the backend stage.

Example:

```
Ref<User>
```

is lowered into a managed runtime reference with metadata.
