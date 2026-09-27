# Formal semantics overview

This chapter defines an abstract model of Norm programs from source declarations to runtime results. It constrains compilers, interpreters, and optimizers without prescribing one internal implementation.

## Program state

Abstract state contains:

- Environment `Γ`: names mapped to static types and declarations;
- Store `Σ`: local bindings, class objects, and value cells addressed by `ref<T>`;
- Control state `K`: current block, call stack, and exception handlers;
- Runtime type table `R`: nominal declarations and reified generic arguments.

## Static judgment

`Γ ⊢ e : T` says that expression `e` has type `T` in environment `Γ`. Assignability is written `S <: T` and arises only from nominal inheritance, interface implementation, safe numeric promotion, nullability, and generic variance rules.

## Evaluation

`⟨e, Σ⟩ ⇓ ⟨v, Σ'⟩` means expression `e` evaluates in state `Σ` to value `v` and new state `Σ'`. Subexpressions evaluate left to right in source order.

## Completion outcomes

A block may produce:

- `Normal(Σ)`: normal completion;
- `Value(v, Σ)`: a control-expression result from `break value`;
- `Return(v, Σ)`: function return;
- `Throw(x, Σ)`: an exception;
- `Break(Σ)` or `Continue(Σ)`: loop transfer.

Type checking ensures these outcomes reach only syntax constructs permitted to receive them.

## Equivalent implementations

Two implementations are semantically equivalent when they produce the same results for every specified observable behavior. An unexposed object layout or the number of physical value copies cannot be observed by ordinary programs. Class identity, ref-location identity, exceptions, I/O order, and reflected type information can be observed.
