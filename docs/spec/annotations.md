# Annotation specification

An annotation is a special class with stable identity. It may declare fields, explicit constructors, and ordinary methods, and may implement interfaces. It cannot declare type parameters or extend a class.

## Policy interfaces

An annotation must implement at least one target interface and exactly one retention policy. The standard definitions are in [`std.annotation`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/annotation/protocols.norm):

- Targets: `PackageTarget`, `TypeTarget`, `FieldTarget`, `ConstructorTarget`, `FunctionTarget`, `ParameterTarget`, `LocalTarget`;
- Interception: `FunctionInterceptor`, `FieldInterceptor<T>`, `ParameterInterceptor<T>`;
- Retention: `SourceRetention`, `BinaryRetention`, `RuntimeRetention`.

Ordinary classes and values cannot implement these policy interfaces. Custom policy interfaces may inherit standard policies. Targets and retention are derived from nominal interface conformance.

```norm
import std.annotation.TypeTarget
import std.annotation.RuntimeRetention

annotation Label implements TypeTarget, RuntimeRetention {
  String text

  String display() {
    return text
  }
}
```

## Construction and application

`@Label(text: "point")` defines one annotation-object construction. Parentheses may be omitted when there are no arguments, as in `@Test`; the compiler still checks required parameters. Arguments must be complete and assignable compile-time values. When a declaration has a parameter named `value`, the first argument may omit its label, as in `@Route("/hello")`; all other arguments must be named. Allowed values include scalar constants and typed declaration references `T.class`, `name.function`, `Owner.name.function`, and `Owner.name.field`. If there is an explicit constructor, its parameters are used; otherwise fields generate constructor parameters. An annotation may also be constructed directly in an ordinary expression, and its fields are mutable.

Core metadata preserves the target identity of a declaration reference, not a string containing its name. Compilation fails if the target is absent or an overload is not unique.

During each execution, one `@` application is constructed once upon its first observation by a function interceptor or runtime reflection. Both paths read the same instance; instances from distinct executions are isolated. An application never observed at runtime does not run its constructor. The same annotation type cannot be applied more than once to one target.

Target interfaces determine only where an annotation may be placed; they do not introduce execution behavior. Interceptor interfaces inherit their corresponding targets and define lifecycles.

## FunctionInterceptor

An annotation implementing `FunctionInterceptor` may override `before`, `around`, and `after`. A marked concrete function or method automatically executes that lifecycle on the declaration side. Direct calls, dynamic dispatch, and function references share one entry point.

Multiple annotations nest in source order. A layer enters only after its `before` completes normally; its `around` decides whether and when to call `proceed()`. After entry, `after` runs whether `around` or the function body returns normally or throws. If that layer's `before` throws, its `after` does not run. An exception from `after` replaces the original completion. `FunctionCompletion.succeeded()` indicates whether that layer's `around` returned normally. `proceed()` may be called at most once.

Callables with `ref` parameters or return types cannot use `FunctionInterceptor`; an interface requirement cannot be intercepted directly.

This executable example combines source-retained interception with a separate runtime-retained annotation on the same function:

<<< ../../norm/tests/docs/language/aop_runtime_metadata.norm{norm}

<<< ../../norm/tests/docs/language/aop_runtime_metadata.out{text}

## ParameterInterceptor

An annotation implementing `ParameterInterceptor<T>` may override `before(ParameterContext, T)` and `after(ParameterContext, FunctionCompletion)`. `T` must exactly equal the declared type of the marked parameter. `ref<T>` parameters and interface-requirement parameters cannot use the parameter lifecycle.

`before` obtains a `Parameter<T>` declaration reference through `context.parameter()`. It may further query `name()`, `type()`, and `function()`. It may validate the argument, throw, or return a new `T` for the callee parameter slot. Input and return values both cross value-copy boundaries. `after` receives neither an argument value nor a reference. It only observes whether this layer completed normally and cannot replace the parameter binding.

Multiple parameter lifecycles enter in parameter-index and annotation-source order, then exit in exact reverse order. When `FunctionInterceptor` is also present, parameter lifecycles run inside its `around` call to `proceed()`; if `around` does not call `proceed()`, they do not run. Direct calls, construction, dynamic dispatch, and function references share the declaration-side entry.

## FieldInterceptor

An annotation implementing `FieldInterceptor<T>` may override `before(FieldContext, T)` and `after(FieldContext, FunctionCompletion)`. `T` must exactly equal the declared field type.

Field initialization, ordinary assignment, and indirect assignment through `ref<T>` share one lifecycle. Through `context.field()`, `before` obtains a `Field<Owner, T>` declaration reference. It may validate, throw, or return a new `T` to store. `after` observes the completion after storage and cannot replace the written value. Multiple annotations enter in source order and exit in reverse order. Inherited fields retain their declaration-side behavior.

`FunctionContext.function()` returns `Function<?>`. Interceptors and tools for logging, validation, and documentation therefore use the same declaration references instead of passing duplicate names or ordinals.

## Document

`std.annotation.Document` is a structured documentation annotation with BinaryRetention. It applies to packages, types, fields, constructors, functions, parameters, and local declarations. [`std.annotation`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/annotation/protocols.norm) defines its fields and reference types.

```norm
@Document(
  description: "Find a user by identifier.",
  types: [User.class],
  functions: [findUser.function],
  fields: [User.id.field]
)
```

In API documentation, `unitTests` is derived from test-side `@Test` associations and is not a constructor parameter of `@Document`. See the [testing API](/stdlib/testing-api) for test declarations and execution rules.

Annotation metadata may contain scalars, declaration references, and `List` literals recursively composed of these values. `List` represents ordered declaration metadata; `Array` is not an annotation metadata type. Non-nullable parameters must be supplied explicitly; omitting a nullable parameter is equivalent to supplying `null`. See [`std.annotation`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/annotation/protocols.norm) for the complete declaration.

The compiler may export `Document` from its retained semantic model directly into a module API tree, together with declarations, types, and source locations. Commands, file mappings, and front-end components are described in [API documentation export](/tooling/api-documentation).

The executable example combines `@Document` on declarations, checked related-declaration references, and a test-side `@Test` association:

<<< ../../norm/tests/docs/language/document_agent.norm{norm}

## Retention and Core

- `SourceRetention` does not produce general metadata. `FunctionInterceptor`, `ParameterInterceptor`, and `FieldInterceptor` behavior remains encoded in callable, parameter, and field Core, respectively.
- `BinaryRetention` produces Core metadata.
- `RuntimeRetention` produces Core metadata and permits reflection reads.

Annotation declarations use the common aggregate Core definition. Application data resides in `CoreArtifact.metadata`, while behavior resides in the interceptor list on the corresponding declaration. Declaration targets use `DefinitionOccurrenceId`, so an identical Core body does not merge distinct source applications.

Reusable parameter and field constraints are documented by the [Validation API](/stdlib/validation-api).
