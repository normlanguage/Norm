# Norm Intermediate Representation Specification

## Purpose

Norm IR is the common representation between frontend and backend.

Targets:

- Truffle execution
- LLVM backend
- Cranelift backend

## Module

A module contains:

```
Types
Functions
Global metadata
Annotations
```

## Function

A function contains:

```
Parameters
Basic blocks
SSA values
Exception handlers
```

## SSA

Every variable assignment creates a unique value.

Example:

Source:

```norm
int c = a + b
```

IR:

```
%1 = load a
%2 = load b
%3 = add %1 %2
store c %3
```

## Control Flow

IR supports:

```
branch
conditional branch
return
throw
invoke
```

## Object Operations

Required instructions:

```
new_object
field_load
field_store
method_call
interface_call
cast
instance_of
```

## Exception Model

Exceptions are represented as control flow edges.

Result<T,E> remains a normal enum value.
