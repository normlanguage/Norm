# Keywords

Keywords are reserved in every source context and cannot be used as ordinary identifiers.

## Declarations

`package`, `import`, `as`, `class`, `interface`, `enum`, `extends`, `implements`, `public`, `private`

## Control flow

`if`, `else`, `for`, `switch`, `case`, `break`, `continue`, `return`, `try`, `catch`, `finally`, `throw`

## Values and type operations

`true`, `false`, `null`, `this`, `super`, `is`, `as`

Basic type names such as `Integer`, `Boolean`, and `Void` are predefined by the language and cannot be redefined either.

## Compatibility

A new keyword can break old source, so stable releases should prefer contextual keywords or enable them through a new language version. The current specification has no backtick-escape syntax for using keywords as identifiers.

Keywords are case-sensitive: `class` is a keyword, while `Class` can be a type name. Keywords use ASCII characters only so visually similar characters cannot hinder review.

`value` is a contextual keyword only in a top-level `value TypeName` declaration header; fields, parameters, locals, and functions may still be named `value`.

`annotation` is a contextual keyword only in a top-level annotation declaration header. Targets and retention policies are ordinary interface names in `std.annotation`.

`extension` is a contextual keyword only in a top-level extension-function declaration header.

Neither `Module` nor `module` is a keyword. The former is a bootstrap interface; the module entry point and bootstrap factory named `module` are ordinary functions. Parameter labels `name`, `version`, `exports`, and `dependencies` are ordinary identifiers.
