# Formal type system

This page defines the core relations used by static judgments. The notation describes the rules and does not prescribe compiler-internal data structures.

## Environments

- `Γ`: local names, fields, functions, and type parameters;
- `Δ`: nominal type declarations, inheritance, and interface implementation;
- `N`: control-flow null state;
- `A`: the definitely assigned set.

The expression typing judgment is written `Δ; Γ; N; A ⊢ e : T`.

## Subtyping

The subtype relation `<:` is the least transitive closure of:

1. Reflexivity: `T <: T`;
2. Declared inheritance: class `extends` and interface `extends`;
3. Implementation: class/value `implements` interface;
4. Nullable promotion: `T <: T?`;
5. Safe numeric promotion;
6. Local capture relations introduced by generic wildcards.

Ordinary `G<S>` and `G<T>` are not subtypes of each other when `S != T`. `ref<T>` is always invariant.

## Null state

For nullable local bindings, `N` tracks `MaybeNull | Null | NonNull`. In the true branch, `x != null` updates `x` to `NonNull`; `x == null` updates it to `Null` in its corresponding branch. Reassignment of a local binding updates its state from the new value. Mutable fields do not enter local smart-cast state: save a field read into a local variable before narrowing it.

## Combining control expressions

If branch results have types `T1...Tn`, the expression type is the unique most-specific `T` satisfying every `Ti <: T`. Reject the expression when there is no unique `T`; the compiler does not synthesize an anonymous union.

## Functions

A call requires every argument type to be assignable to its corresponding parameter. Function-value compatibility requires contravariant parameters and a covariant return, while public parameter names in named calls must also be compatible. Overload selection must yield one uniquely best candidate after generic substitution.

## Definite assignment

`A` tracks initialized bindings. Reading `x` requires `x ∈ A`. After an `if`, intersect the sets from both branches. Because a loop body may execute zero times, an ordinary loop does not add variables assigned only in its body to the subsequent set.

## Soundness target

A program that passes type checking should not enter undefined behavior because of a missing member, unsafe nullable dereference, or mismatched generic argument. Exceptions, failed explicit casts, and resource errors remain permitted runtime outcomes.
