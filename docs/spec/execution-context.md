# Execution context

`std.context` provides lexical contexts distinguished by complete type identity. The public entry point is [context.norm](../../norm/stdlib/std/context/context.norm).

- `currentContext<T>()` returns the current binding as `T?`, or `null` when unbound.
- `withContext(value:, action:)` binds a value while the action runs and returns the action result; a separate overload handles actions without a return value.
- An inner binding of the same type temporarily shadows the outer one, which is restored after normal return or failure. Different types and distinct generic arguments have separate bindings.
- A binding belongs to the current execution instance and calling thread. An ordinary newly created thread does not inherit it automatically. Frameworks delivering callbacks across threads must explicitly restore the callback's context.
- A context does not copy its objects or make their state thread-safe. GUI component state remains governed by UI-thread rules.

The runtime implementation reuses JDK ScopedValue; see [ExecutionContexts](../../cli/compiler/src/main/java/dev/w0fv1/norm/truffle/ExecutionContexts.java). [ContextExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/ContextExecutionTest.java) verifies language behavior, and [ExecutionContextsTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/ExecutionContextsTest.java) verifies thread isolation.

`std.concurrent.startTask` submits work on a virtual thread and returns `Task<T>`; see [tasks.norm](../../norm/stdlib/std/concurrent/tasks.norm). Tasks use the execution instance's resource management and preserve Norm types and object identity in their results; see [TaskExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/TaskExecutionTest.java). A task captures the current ResourceOwner, and later stages inherit ownership. Completion and cancellation release that ownership. The executor still supplies UI callback scheduling.

## Resource ownership

A host scope may bind `std.io.ResourceOwner`; see the [ownership contract](../../norm/stdlib/std/io/ownership.norm). `own` takes ownership of a resource, `release` removes a released resource, and `execute` synchronously invokes a callback in its owning context. This does not require the standard library to depend on a GUI or replace a thread scheduler.

Field subscriptions capture their resource owner at creation and invoke change callbacks in its context. Closing a subscription early releases ownership; closing the scope releases remaining subscriptions. Without a bound resource owner, subscriptions may still be managed manually. See [declaration references](declaration-references.md) for the field-observation contract.
