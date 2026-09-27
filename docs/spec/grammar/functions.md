# Function Declaration Syntax

```text
Function := Visibility? "extension"? ReturnType? Identifier TypeParameters?
            "(" Parameters? ")" Block
Parameter := Type Identifier ("=" Expression)?
```

```norm
Integer subtract(Integer left, Integer right) {
    return left - right
}
```

## Extension functions

`extension` modifies only top-level functions and requires an explicit return type and at least one parameter. The first parameter is the receiver; remaining parameters follow ordinary function-label rules.

```norm
extension String quoted(String value) {
  return "\"" + value + "\""
}

String text = "Norm".quoted()
```

After binding, a dotted call becomes an ordinary function call, with the receiver evaluated first as its first argument. The extension must be in the current package or explicitly imported. An instance method wins by name; several equally matching extensions are a compile error. Extensions enter neither a type's method table nor its dynamic dispatch table.

An extension checks a nullable receiver by the declared type of its first parameter, not by dereferencing an instance early. For example, a first parameter of `String?` can receive null; an expected `FieldHandle<T>` can capture a field. Instance methods and function-typed fields still follow ordinary null-access checks and cannot bypass them through a same-named extension.

## Parameters

Parameters are local bindings in a function body. A multiparameter call uses `name: value`, making parameter names part of the public API. A single-parameter call may omit the name; an unlabelled bare identifier in a multiparameter call can omit its label only if it has the same name as the corresponding parameter. Default parameters follow required ones. When an argument is omitted, its default expression evaluates at the call site in parameter order.

Labels determine which parameter receives a result, but all argument expressions always evaluate left to right in source order. Unknown, duplicate, and missing labels are compile errors; `name = value` is not call syntax.

```norm
Integer result = subtract(left: 120, right: 100)
```

## Returns

An ordinary top-level function without a return type has declared type `Void`. Extensions must declare a return type. A class method without an explicit return type has the complete owner type: normal fallthrough and bare `return` produce `this`, and `return value` is invalid. Explicit `Void` always means no result.

Otherwise, every normally completing path of a named non-`Void` function must produce a result, using `return value` or a trailing expression. The branches of a trailing if follow the same rule. Interface methods must explicitly declare a return type. See [advanced function rules](/spec/grammar/functions-advanced) for trailing expressions in lambdas.

Functions may be declared at module top level or inside a type. Top-level functions need no class container, and there is no `static` modifier. See [advanced function rules](/spec/grammar/functions-advanced) for overloads and function values.

The executable example combines named and default arguments, argument shorthand, an interface contract, and an `if` result:

<<< ../../../norm/tests/docs/language/calls_interfaces.norm{norm}

<<< ../../../norm/tests/docs/language/calls_interfaces.out{text}
