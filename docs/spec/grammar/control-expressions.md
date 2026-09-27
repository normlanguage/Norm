# Control-Flow Expressions

See [expression syntax](expressions.md) for the grammar and result rules of if, for, and switch.

`break value` ends the innermost control expression being evaluated. `return` exits the enclosing function, while `throw` propagates under exception-handling rules. An if result branch may also use a trailing expression. When used as an expression, every normally completing path must produce a result of compatible type.
