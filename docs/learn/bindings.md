# 02 Explicit types and assignment

A binding gives a value a name and a definite static type.

<<< ../../norm/tests/docs/tour/02_bindings.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/02_bindings.out{text}

`Integer remaining` and `String task` place the type before the variable name. Each local variable is initialized when declared. Later assignment to `remaining` replaces its stored value and must still produce an `Integer`.

Try it: Assign a string to `remaining` and read the compiler's type diagnostic.

Precise rules: [Language Reference](/spec/type-system).
