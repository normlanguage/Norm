# Advanced Function Rules

This page adds static rules for overloads, function values, lambdas, closures, and method references. Start with [functions and calls](/learn/functions) for an introduction.

## Overload resolution

Candidate functions are collected by name and visibility, then filtered by parameter count, labels, types, and generic inference in that order. A call must resolve to one target. Return type does not participate in overload identity or break ambiguity.

```norm
String format(Integer value) { return "integer" }
String format(String value) { return value }

String text = format(value: 3)
```

## Function types and values

A function type records its return and parameter types in full:

```norm
Function<Integer(Integer)> transform
Function<Boolean(String)> predicate
Function<Void()> action
```

A parameter declaration may use the equivalent callable form:

```norm
R mapValue<T, R>(R transform(T value), T value) {
  return transform(value)
}
```

`var` still infers a complete function type; there is no raw `Function`.

`Function<R(P...)>` does not declare parameter names. Call its function values positionally, such as `combine(first, second)`; compiler-internal parameter labels are not accepted. Named callable parameters retain their declared names and label rules.

## Lambdas and closures

Ordinary functions, methods, getters, interface default methods, and lambdas may omit `return` for a trailing expression. Branches of a trailing `if / else` follow the same rule. Every normally completing path must yield a value compatible with the return type; early exits use explicit `return`. `Void` functions produce no result, while fluent methods without a return type still return the receiver.

```norm
Integer doubled(Integer value) { value * 2 }
Integer choose(Boolean first) {
  if first { 1 } else { 2 }
}
```

```norm
var doubled = (Integer value) { value * 2 }
Function<Integer(Integer)> tripled = (value) { value * 3 }
var quadrupled = Integer(Integer value) { value * 4 }
```

A lambda's result type uses constraints from the expected function type and trailing expression. With an explicit return type, control-flow paths are checked like an ordinary function.

A lambda may capture surrounding locals, parameters, and `this`. Captured locals and parameters must be effectively final. Captured classes retain object identity; other values follow ordinary assignment semantics.

## Trailing lambdas

Explicit type arguments may directly precede a trailing lambda, such as `submit<Integer> { 42 }` or `runner.run<List<String>> { ["Norm"] }`; empty argument parentheses are unnecessary. The formatter omits them when the lambda is the only argument. Braces in control structures still follow the boundary rules below.

A call can place one lambda after its argument parentheses; when it passes only that lambda, it may omit call parentheses. The parameterless form is `{ body }`, and a parameterized form is `{ name, other in body }`. The expected function type infers parameter types. `in` is a separator only in this parameter header.

When an API uses a named callback signature, a trailing lambda without a parameter header automatically receives the contract's parameter names and types:

```norm
Void submit(Void completed(String title)) { completed("任务") }
Void main() { submit { printLine(title) } }
```

These parameters belong to the lambda's own scope and may shadow outer variables of the same names; nested closures follow ordinary capture rules. Explicit `{ other in ... }` uses the caller's parameter name. Explicit `() { ... }` always means zero parameters. A callback declared only as `Function<Void(String)>` has no parameter-name contract, so the caller must declare its parameter explicitly. The editor provides types and completion for implicit parameters; use an explicit header to rename one.

```norm
button(text: "添加") { store.submit() }
runApp(title: "待办清单") { scope in
  todoScreen(scope: scope, path: path)
}
```

A trailing lambda binds to the last function-typed parameter in the declaration. Configuration parameters after it still follow ordinary default and label rules. Other required parameters cannot be omitted, and an argument cannot be supplied twice. Type inference, overload selection, closure capture, and evaluation order reuse ordinary lambda-argument rules.

When all parameters after the first have defaults or are supplied by the trailing lambda, the first parameter may omit its label, as in `Button("All") { reload(null) }`. Other required parameters still require labels.

`Type() { ... }` and `Type(value) { ... }` are parsed as constructor calls. A lambda form with an explicit return type needs a nonempty parameter list with an explicitly typed first parameter, such as `Integer(Integer value) { value * 2 }`. A parameterless lambda uses `() { ... }`, with its return type inferred from the expected type or trailing expression.

Top-level braces after an `if` condition, `for` condition or iterable, or `switch` input belong to the control structure. Parenthesize a trailing-lambda call in those positions, for example `if (test() { true }) { ... }`.

## Block call chains

A call that has just ended a trailing lambda may continue on the same line with an ordinary member call that takes only a trailing lambda:

```norm
async {
  repository.findByCompleted(completed)
} then {
  todos = result
}
```

This equals `async { ... }.then { ... }`, not two callback arguments to one call. Segments associate left to right; each later segment receives the preceding result. The complete expression has the last call's return type. The rule applies to ordinary and extension members, not only Task, and does not make `then` or `error` keywords.

The dot may be omitted only when all of these conditions hold:

| Boundary | Requirement |
| --- | --- |
| Predecessor | The current expression is a call, and its most recently consumed real `}` ends that call's own trailing lambda. |
| Successor | The next two tokens are `IDENTIFIER` and `{`; the successor passes only one trailing lambda. |
| Line and separator | The predecessor `}`, member name, and successor `{` are on one line with no other token between them. LF, CRLF, and CR all use source-line indexing. |
| Control structure | The current expression depth permits a trailing lambda; calls in conditions and iterable sources still need parentheses. |

`produce{}map{}` is recognized as well; the formatter emits `} map {`. Closure bodies can span lines, but connecting heads cannot. Other successor parameters may be omitted only when existing default-argument rules permit it.

```norm
produce { work() } map { item in transform(item) } finish { save(result) }
produce { work() }; independent { consume() }
```

A newline between `}` and the name, or between the name and `{`, does not form a dotless chain; in a statement list it may instead mean an independent call. Two independent trailing-closure calls on one line need a semicolon separator, or the later call must start on another line. String content does not participate in chain recognition, interpolation follows ordinary expression rules, and this rule adds no comment syntax.

`task map { ... }`, `produce() map { ... }`, and `(produce { ... }) map { ... }` are not block call chains. A chain cannot cross `)`, `]`, subsequent property access, or the `}` of a control structure to attach to an earlier closure. When a successor needs explicit type arguments, ordinary arguments, or safe access, use `.map<R> { ... }`, `.map(option: value) { ... }`, or `?.map { ... }`.

Connection is an ordinary postfix call. For example, in `left + produce { ... } map { ... }`, `map` operates on the result of `produce`, not the entire addition. Once a chain forms, its target resolves only as a member call; a missing member, overload ambiguity, or type error does not fall back to a same-named top-level function.

Each callback segment has its own parameter scope; implicit parameter names come from that segment's named callback signature. Capture, defaults, generics, Void/Unit, nullability, class identity, value copying, and `ref<T>` rules are identical to the dotted form. The receiver evaluates once. A chain adds no waiting, thread switch, automatic dereference, safe access, or nested-Task flattening, and does not change the targets of `return`, `break`, or `throw`. See the [Concurrency API](/stdlib/concurrency) for Task execution.

Formatting normalizes to a dotless form only when the complete call tree satisfies these structural conditions. It neither merges independent statements or result-builder elements nor guesses at rewrites for syntax-error sources. A connecting head is not line-wrapped to meet width limits.

## Function and method references

To accumulate several expressions in a content block into one result, use a [result builder](/spec/grammar/result-builders).

```norm
Function<Integer(Integer)> first = doubled
Function<Integer(Integer)> second = counter.add
Integer add(Integer amount) = counter.add
Function<Integer(Counter, Integer)> unbound = Counter.add.function
Function<?> declaration = Counter.add.function
```

A top-level function converts directly to an expected function type. `receiver.method` creates a function value bound to the receiver. `Owner.method.function` is an unbound declaration reference: its exact signature has the receiver as its first parameter, and a call still dispatches by the receiver's dynamic type. A top-level declaration uses `name.function`.

An overloaded reference must resolve uniquely under an exact expected `Function<R(P...)>`. `Function<?>` retains declaration identity and queryable metadata only and cannot be called directly. Function values can be stored in fields, passed as arguments, returned, and called as `operation(value)`. See [declaration references and reflection](/spec/declaration-references) for uniform rules.

## Recursion and generics

A function may recurse directly or indirectly. A generic function declares type parameters after its name:

```norm
T identity<T>(T value) {
  return value
}
```

Type inference uses only call arguments and explicit expected types; it does not analyze a named function body to infer its public signature.
