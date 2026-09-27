# Class Declarations

A class represents an object with stable identity that may contain mutable state and behavior.

```norm
class Counter {
    Integer value

    Counter(Integer initial) {
        value = initial
    }

    Void increment() {
        value = value + 1
    }
}
```

## Members

A class can declare fields, one explicit constructor, and methods. The constructor has the class name without a return type or visibility modifier. A root class without an explicit constructor uses labeled field construction. Every normally exiting path of an explicit constructor must initialize ordinary fields declared by that class.

A private field with an initializer is internal object state and is not an implicit constructor parameter; its initializer runs for each new object. Public ordinary fields remain constructor inputs, and defaults let callers omit the corresponding arguments. An ordinary field without an initializer still needs a constructor input. Internal state fields do not affect the required-before-default parameter ordering of constructor inputs.

An annotation implementing `std.annotation.ManagedField` delegates responsibility for initializing a class field to an external manager. Such a field is neither an implicit constructor parameter nor required to be assigned in an explicit constructor. Its static type remains unchanged; reading it before initialization throws a catchable exception. A constructor still cannot read an uninitialized field. This contract cannot apply to value fields; the manager guarantees construction and injection order.

## Computed properties

Classes and values may declare accessor-backed properties that occupy no stored field. A getter is required; omitting the setter makes the property read-only. The setter parameter has the property type, and its return type is fixed as Void.

```norm
class Counter {
    private Integer stored

    Counter(Integer initial) { stored = initial }

    Integer value {
        get { return stored }
        set(next) { stored = next }
    }
}

var counter = Counter(1)
counter.value = 2
printLine(counter.value)
```

Accessors follow ordinary method rules for returns, generics, closures, and dynamic dispatch. A property read calls the getter. Assignment evaluates the receiver, then the right side, once each, then calls the setter. `private set(next)` restricts external writes; safe navigation cannot be an assignment target. A value setter may call an external object or function but still cannot modify stored fields of that value.

Computed properties enter neither default constructor parameters nor field storage. Properties and stored fields cannot hide each other within a class or inheritance chain. A parent's private computed property is not inherited. A function-typed property result can be called directly. The current interface declaration syntax still uses method signatures.

Inside an instance, a property receiver's `this` may be omitted, as in `value = value + 1`; a same-named local or parameter takes precedence. This also applies to inherited properties and closure access, still subject to getter/setter visibility. A function-typed property can be written `transform(input)` directly.

Overriding a public property must keep public visibility. A read-only property's getter return type may be covariant. Overriding a public writable property must retain the same property type and explicitly preserve a public setter. A setter parameter name is local to the accessor and does not affect override dispatch.

## Inheritance

A class may directly extend at most one class and implement several interfaces. When a subclass omits a constructor, it keeps its own field-constructor parameters and automatically calls a parent constructor available with no arguments; parent defaults follow ordinary call rules. If no suitable parent constructor exists, compilation fails. An explicit subclass constructor must place `super(...)` first. Parent fields are initialized by the parent constructor; the subclass constructor initializes only its own declared fields. Fields cannot hide inherited fields.

```norm
class TimedCounter extends Counter implements Printable {
    Instant updatedAt

    TimedCounter(Integer initial, Instant now) {
        super(initial: initial)
        updatedAt = now
    }
}
```

Public methods override by parameter labels, parameter types, and generic shape; they participate in dynamic dispatch through class and interface calls. Return types may be covariant. Private methods are neither inherited nor overridden. Same-named overloads are gathered across the whole inheritance chain; a subclass declaration does not hide other parent overloads. An overridden method retains only the most specific implementation candidate. Ordinary calls and bound or unbound method references use the same candidate set.

A class annotated with a `std.annotation.ManagedImplementation` contract may declare public methods without bodies, but must explicitly declare their return types. A class that still has unimplemented methods, and its subclasses, cannot be constructed directly. A subclass may be constructed after implementing all inherited methods. See [Java Library Adapter](/design/java-library-adapters) for framework implementation and Java projection.

Class assignment, argument passing, and return preserve dynamic type and object identity. `copy()` creates a new top-level object identity; see [Value and Identity Semantics](/spec/value-identity-semantics) for field-copy rules.
