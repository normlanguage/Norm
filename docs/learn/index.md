---
title: Learn Norm
description: One language feature at a time, with runnable examples
---

# Learn Norm

Each lesson introduces one feature through a runnable program, its checked output, an explanation, and one change to try. Follow the order for a gradual start, or open a specific topic.

The full learning path is checked against [Norm 0.25](/versions/0.25). Use [Status](/status) and the [version index](/versions/) to check the capabilities of an installed release.

## Write and run a program

| Lesson | New feature |
| --- | --- |
| [01 Program entry](/learn/hello) | Start a single-file program with `main()` |
| [02 Explicit types and assignment](/learn/bindings) | Declare and update typed variables |
| [03 Type inference](/learn/inference) | Let an initializer determine a local type |
| [04 String interpolation](/learn/interpolation) | Place expressions inside strings |
| [05 Operators and comparisons](/learn/operators) | Compute numbers and Boolean results |
| [06 Conditional execution](/learn/conditionals) | Choose a block with `if/else` |

## Organize calculations with functions

| Lesson | New feature |
| --- | --- |
| [07 Functions and explicit returns](/learn/functions) | Name a calculation and return its result |
| [08 Named arguments](/learn/named-arguments) | Bind call values to parameter labels |
| [09 Default arguments](/learn/default-arguments) | Omit an argument with a declared default |
| [10 Argument shorthand](/learn/argument-shorthand) | Reuse matching local and parameter names |
| [11 `if` as a result](/learn/if-results) | Return a value from either branch |
| [12 Final-expression returns](/learn/final-expression) | Use the last expression as a function result |

## Work with collections and control flow

| Lesson | New feature |
| --- | --- |
| [13 Lists](/learn/lists) | A list stores an ordered sequence whose length can change. |
| [14 Iteration](/learn/iteration) | Use `for` to visit each element without managing an index yourself. |
| [15 Conditional loops](/learn/conditional-loops) | A condition-style `for` repeats while its Boolean condition remains true. |
| [16 Break](/learn/break) | A valueless `break` leaves the current statement loop immediately. |
| [17 Continue](/learn/continue) | `continue` skips the rest of one iteration and proceeds to the next. |
| [18 Arrays](/learn/arrays) | An array is indexed like a list but keeps a fixed length. |
| [19 Maps](/learn/maps) | A map associates a typed key with a typed value. |
| [20 Sets](/learn/sets) | A set keeps distinct elements and answers membership queries. |
| [21 Collection value semantics](/learn/collection-values) | Assigning a built-in collection gives the new binding an independent container value. |
| [22 Collection `for` elements](/learn/collection-for) | A collection literal can generate elements from another iterable. |
| [23 Collection `if` elements](/learn/collection-if) | A collection literal can include an element only when a condition holds. |
| [24 Spread in collections](/learn/spread) | `...` inserts the elements of an iterable at one position in a collection literal. |

## Model values and entities

| Lesson | New feature |
| --- | --- |
| [25 Value declarations](/learn/value-declarations) | A `value` groups immutable fields into a typed data value. |
| [26 Value equality](/learn/value-equality) | Values compare by their contents rather than by creation event. |
| [27 Class declarations](/learn/class-declarations) | A `class` models an entity with fields and behavior that can change. |
| [28 Class identity](/learn/identity) | Class variables can refer to the same entity or to distinct entities with equal-looking fields. |
| [29 Copying a class](/learn/copying) | Call `copy()` when a new top-level class identity is needed. |
| [30 Explicit constructors](/learn/constructors) | A class can replace implicit field construction with a same-named constructor. |
| [31 Computed properties](/learn/computed-properties) | A computed property presents accessor behavior with field-like syntax. |
| [32 Fluent methods](/learn/fluent-methods) | A method may return `this` so the caller can continue operating on the same object. |

## Handle absence and match states

| Lesson | New feature |
| --- | --- |
| [33 Nullable values](/learn/nullable-values) | A `?` on a type permits `null`; the type without `?` does not. |
| [34 Flow narrowing](/learn/flow-narrowing) | A null check can prove a nullable value non-null inside a branch. |
| [35 Safe access](/learn/safe-access) | `?.` reads a member only when its receiver exists. |
| [36 Null fallback](/learn/fallback) | `??` uses its right operand only when the left operand is null. |
| [37 Plain enums](/learn/plain-enums) | An enum lists a finite set of named alternatives. |
| [38 Data enums](/learn/data-enums) | Enum alternatives may carry differently typed data. |
| [39 Switch results](/learn/switch-results) | `switch` can produce a result while extracting an enum variant's payload. |
| [40 Exhaustive matching](/learn/exhaustive-matching) | A switch over a closed enum must account for every variant. |
| [41 Nested patterns](/learn/nested-patterns) | A pattern may match a variant inside another variant's payload. |

## Compose abstractions

| Lesson | New feature |
| --- | --- |
| [42 Interface declarations](/learn/interface-declarations) | Specify a callable contract. |
| [43 Implementing an interface](/learn/interface-implementation) | Provide the required behavior. |
| [44 Default interface methods](/learn/default-implementations) | Share behavior in the contract. |
| [45 Class inheritance](/learn/inheritance) | Extend a class and initialize its base. |
| [46 Overriding methods](/learn/overrides) | Dispatch to a subclass implementation. |
| [47 Generic types](/learn/generic-types) | Parameterize a data type. |
| [48 Generic functions](/learn/generic-functions) | Parameterize a calculation. |
| [49 Generic inference](/learn/generic-inference) | Infer type arguments from call values. |
| [50 Matching generic types](/learn/retained-generic-matching) | Distinguish generic instantiations at runtime. |
| [51 Function overloads](/learn/overloads) | Select a signature by argument type. |

## Functions as values

| Lesson | New feature |
| --- | --- |
| [52 Function values](/learn/function-values) | Store and pass callable values. |
| [53 Lambdas](/learn/lambdas) | Create a function value at its use site. |
| [54 Capturing values](/learn/capture) | Keep access to an enclosing value. |
| [55 Bound method references](/learn/method-references) | Bind a callable to an object. |
| [56 Extension functions](/learn/extensions) | Call a static function through receiver syntax. |
| [57 Trailing callbacks](/learn/trailing-callbacks) | Supply a final callback as a block. |
| [58 Named callback parameters](/learn/named-callbacks) | Name a callback's supplied value. |
| [59 Chaining block calls](/learn/block-call-chains) | Compose functions with trailing blocks. |

## Build a project

| Lesson | New feature |
| --- | --- |
| [60 Imports](/learn/imports) | Use a public declaration from another package. |
| [61 Modules](/learn/modules) | Describe a module's name, version, and exports. |
| [62 Packages](/learn/packages) | Give source files structured names. |
| [63 Visibility](/learn/visibility) | Separate public API from file-private details. |
| [64 Local dependencies](/learn/dependencies) | Consume a packaged library through the module graph. |
| [65 Tests](/learn/testing) | Declare and run a test. |
| [66 Exception handling](/learn/exceptions) | Catch exceptional control flow. |
| [67 Cleanup with finally](/learn/cleanup) | Run cleanup on every exit. |

## Describe declarations

| Lesson | New feature |
| --- | --- |
| [68 Type declaration references](/learn/type-references) | Read type metadata through a checked declaration. |
| [69 Field declaration references](/learn/field-references) | Read and write a field through its descriptor. |
| [70 Function declaration references](/learn/function-references) | Use a checked callable declaration reference. |
| [71 Annotation targets](/learn/annotation-targets) | Restrict where an annotation may appear. |
| [72 Annotation retention](/learn/annotation-retention) | Choose how long annotation data remains available. |
| [73 Runtime annotation metadata](/learn/runtime-annotations) | Read a runtime-retained annotation. |
| [74 Function interception](/learn/function-interception) | Wrap a function call with `around`. |
| [75 Interceptor entry and exit](/learn/interceptor-order) | Observe `before`, body, and `after` order. |
| [76 Documenting declarations for agents](/learn/document-annotation) | Expose checked intent through `@Document` and query. |

## Observe and build

| Lesson | New feature |
| --- | --- |
| [77 Lexical references](/learn/lexical-references) | Borrow a writable local location. |
| [78 Field handles](/learn/field-handles) | Bind a field descriptor to an object. |
| [79 Field subscriptions](/learn/field-subscriptions) | Watch assignments and close a subscription. |
| [80 Collection change notifications](/learn/collection-changes) | Watch collection mutations through a field. |
| [81 Resource ownership](/learn/resource-ownership) | Register and release owned subscriptions. |
| [82 Result builders](/learn/result-builders) | Accumulate expressions into a result. |
| [83 Control flow in result builders](/learn/builder-control-flow) | Accumulate selected branches and loop iterations. |

## Use libraries

| Lesson | New feature |
| --- | --- |
| [84 JSON value roundtrip](/learn/json-roundtrip) | Encode and decode a checked value. |
| [85 Writing and reading a file](/learn/file-write) | Claim a new file and read it back. |
| [86 Typed file failures](/learn/file-failures) | Handle a missing file by failure category. |
| [87 A packaged library](/learn/commons-lang) | Consume an external library through a direct dependency. |

For a complete GUI Todo and Web guestbook, use [the generated `norm hello` programs](/tooling/#start-with-examples) with Norm 0.25.

The [Language Reference](/spec/language-spec) defines precise rules; [Status](/status) describes released capabilities.

Start with [Program entry](/learn/hello).
