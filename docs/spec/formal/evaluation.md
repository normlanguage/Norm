# Core evaluation rules

Norm evaluates deterministically from left to right. Compiler optimizations must not change the observable order of exceptions, function calls, object mutations, or external I/O.

## Calls

For `f(a: e1, b: e2)`:

1. Resolve the unique target function.
2. Evaluate arguments in source order.
3. Establish a new local environment by parameter name.
4. Establish logically independent values for value arguments; preserve identity for class arguments.
5. Execute the function body until Return, Throw, or normal Void completion.

## If

Evaluate the Boolean condition first, then exactly one branch. In expression form, the executed branch must end in Value or abnormal completion. The compiler does not insert null for a missing `else`.

## For

Evaluate the iterable expression once and obtain its iterator. Each iteration creates a fresh loop-variable binding. `continue` requests the next item; a valueless `break` normally ends a statement loop; `break value` ends an expression loop. Run the optional `else` on exhaustion.

## Switch

Evaluate the matched expression once. Check cases in source order; only the first matching case executes, without fallthrough. Static checking has already established exhaustiveness for every switch. Every normally completing expression case must produce its result through `break value`.

## Exceptions and finally

Throw searches the call stack for the first catch compatible with its dynamic type. Normal, Return, Throw, Break, and Continue completions of a try all run `finally` before leaving. If `finally` completes normally, restore the original completion; a completion produced by `finally` replaces it. Toolchain runtime errors bypass user catches but still run `finally`.

## Assignment

Determine the target location first, then evaluate the right-hand side. A value write stores a logically independent value; a class write stores an object reference. Failure while evaluating the right-hand side leaves the target unchanged. See [value and identity semantics](/spec/value-identity-semantics) for the full rules.
