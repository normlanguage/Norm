# Declaration references and reflection

Norm binds references to types, fields, and callables to Core declaration identities. The toolchain updates references when declarations are renamed. Compilation fails when a target is deleted, invisible, or ambiguous among overloads. Runtime APIs do not accept string declaration names or public ordinals.

## Reference syntax

| Target | Syntax | Type |
| --- | --- | --- |
| Type | `User.class` | `Class<User>` |
| Nullable or generic type | `String?.class`, `List<String>.class` | `Class<String?>`, `Class<List<String>>` |
| Field | `User.id.field` | `Field<User, UserId>` |
| Top-level function | `findUser.function` | `Function<User(UserId)>` |
| Unbound method | `UserService.findUser.function` | `Function<User(UserService, UserId)>` |
| Bound method | `service.findUser` | `Function<User(UserId)>` |

`.function` names the declaration; ordinary member access produces a callable bound function value. An unbound method puts an owner parameter named `this` first in its signature, while calls still dispatch dynamically.

## Typed metadata

| Type | Stable capabilities |
| --- | --- |
| `Class<T>` | `name()`, `isValue()`, `annotation<A>()`, `fields()`, `functions()`, `constructors()` |
| `Field<Owner, Value>` | `name()`, `type()`, `owner()`, `annotation<A>()`, `isPublic()`, `hasAnnotation(Class<A>)`, `identity(Owner)`, `read(Owner)`, `bind(Owner)`, `write(receiver: Owner, value: Value)`, `copy(target: Owner, source: Owner)` |
| `Function<Signature>` | `name()`, `owner()`, `parameters()`, `annotation<A>()` |
| `Parameter<Value>` | `name()`, `type()`, `function()`, `annotation<A>()` |
| `Constructor<T>` | `owner()` |

`Field<Owner, Value>.type()` returns `Class<Value>`, and `owner()` returns the descriptor's `Class<Owner>`. When `Class<T>.fields()` enumerates inherited fields, `Owner` is the current `T` view; a direct `Base.value.field` instead has `Base` as its owner. `read(receiver: ...)` requires an `Owner` instance and returns the field's exact `Value` type.

`Class<T>.fields()` returns `List<Field<T, ?>>`, `functions()` returns `List<Function<?>>`, and `constructors()` returns `List<Constructor<T>>`. Every overload is a separate element. Heterogeneous collections use `?` to hide different field value types or function signatures.

Top-level functions and bound methods can be put directly into `List<Function<?>>`. When overloads exist, first select a declaration with an exact function type. `annotation<A>()` on functions and parameters queries RuntimeRetention annotations and shares an annotation instance with interceptors. See the [JSON API](/stdlib/json-api) for executing heterogeneous functions through a serialization protocol.

Computed properties do not create storage fields, so they add no entry to `fields()`. Accessors appear in `functions()`; getters and setters keep the same property name but distinct callable identities, and their parameter lists include the receiver. `PropertyExecutionTest.reflectsPropertyAccessorsWithoutInventingStorageFields` verifies this.

## Runtime objects and field copying

`classOf(value)` returns a `Class<T>` for the object's actual type, with `T` retaining the argument's static type. Even when an interface or superclass holds the object, `fields()` enumerates fields of its actual type. Equality and hashing of `Class<?>` descriptors depend only on the represented type, not the call site's static view. `isValue()` reports whether the type has value semantics.

`Field.bind(receiver)` produces a `FieldHandle<Value>` holding a mutable class instance and field location. A handle may be returned, stored in an object, or captured by a closure. `read()` reads the current value; `write(value)` uses the normal field-writing path. Handles to the same field of the same object compare equal; handles of different objects do not. A handle does not capture a local-variable location or change the lexical lifetime rules of `ref<T>`. Where `FieldHandle<T>` is expected, an accessible class storage field may be passed directly. For example, `binding(model.title)` captures the field automatically, evaluating the receiver once. An existing handle passes through unchanged, and a generic call may infer `T` from the field type. Local variables, computed properties, value fields, and null-safe field access do not undergo this conversion.

`Field.write(receiver: ..., value: ...)` writes a mutable class using the exact field value type, reusing normal field writing, annotation interception, and change notification. Runtime checks validate the receiver and actual field type. A heterogeneous `Field<T, ?>` cannot accept an arbitrary value for writing.

After importing `std.observation.onChange`, `model.title.onChange { ... }` subscribes to field assignments. This extension receives a `FieldHandle<T>` and reuses field capture. Callback parameters `oldValue` and `newValue` retain the field's nullable type. Subscription does not invoke the callback immediately and filters equal assignments according to field-value equality. It returns a `Resource` whose `close()` may be called repeatedly to stop the subscription.

A subscription automatically joins the [resource-ownership context](execution-context.md#resource-ownership) at creation. GUI components provide that context, so subscriptions registered in `init()` or a component callback are released when the component is destroyed, and change callbacks restore that component context. An early `close()` releases ownership immediately.

Each notification carries snapshots of that change's old and new values. A nested modification in a callback does not overwrite the outer notification's parameters. If a subscription callback throws an ordinary exception, other active subscriptions still receive that change; the exception then propagates to the modifying caller.

Whole-field replacement of a collection, in-place mutation, and mutation of nested value containers are observable. Equal replacement and operations that leave content unchanged do not notify. After replacing the field or a nested container, the subscription follows the field's current value. A copied collection does not inherit subscriptions. Changing a field inside a class element is not a structural container change; subscribe to that object's field instead. See [FieldHandleExecutionTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/truffle/FieldHandleExecutionTest.java) for implementation and execution coverage.

`Field.isPublic()` reflects the field declaration's visibility. Under the same field-descriptor constraint, `Field.copy(target: ..., source: ...)` copies a field through ordinary field writing, annotation interception, and change notification. Runtime checks validate both objects' types and generic arguments. The target must be a mutable class; reflection cannot mutate a value. A heterogeneous field collection may call `copy` directly without reducing field values to `Any`.

See [ObjectReflectionExecutionTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/truffle/ObjectReflectionExecutionTest.java) for runtime verification.

`Field.hasAnnotation(Class<A>)` checks whether a runtime-retained annotation has the requested annotation type or implements the requested interface, without instantiating the annotation. `std.annotation.IdentityField` is a general marker for identity fields.

`Field.identity(receiver)` returns an opaque value type, `FieldIdentity`, whose equality and hash include the owner type, field declaration, and a snapshot of the field value without string conversion. The field value follows Norm's value/object identity semantics; null does not constitute an identity. Heterogeneous reflected fields can create identities without exposing hidden field value types. See [FieldIdentityExecutionTest](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/test/java/dev/w0fv1/norm/truffle/FieldIdentityExecutionTest.java).

## Overloads

When assigning an overloaded declaration reference to an exact function type, the compiler uses the expected signature to choose one declaration:

```norm
Function<User(UserService, UserId)> lookup = UserService.findUser.function
```

`Class<T>.functions()` and `constructors()` instead retain every overload for metadata queries. A `Function<?>` has no known callable signature and therefore cannot be invoked directly.

## Implementation sources of truth

[`BuiltinCatalog`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/builtin/BuiltinCatalog.java) defines reflection members and exact types. [`CoreAnnotationReference`](https://github.com/normlanguage/Norm/blob/main/cli/compiler/src/main/java/dev/w0fv1/norm/core/CoreAnnotationReference.java) represents declaration references in binary metadata.
