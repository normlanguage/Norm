# 01 Program entry

A standalone `.norm` file starts at its top-level `main()` function.

<<< ../../norm/tests/docs/tour/01_hello.norm{norm}

Output:

<<< ../../norm/tests/docs/tour/01_hello.out{text}

The braces contain the work performed when the program starts. `printLine` writes one line to standard output. `main()` has no declared result type here, so it is a `Void` entry point. The whole program needs only this one source file.

Save the code above as `hello.norm`, then run `norm hello.norm` in that directory with a released CLI.

Try it: Replace the greeting text with your name, save the file, and run it again.

Precise rules: [Language Reference](/spec/grammar/declarations).

Next: [Explicit types and assignment](/learn/bindings).
