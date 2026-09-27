# 01 Hello, Norm

A Norm program consists of `.norm` source files and top-level declarations. A standalone script only needs `main()`.

<<< ../../norm/tests/docs/tour/01_hello.norm{norm}

Output:

```text
Hello, Norm
```

`main()` is the program entry point. Because it omits a return type, it is a `Void` top-level function. `String language` declares the type before the name, and `printLine` is a core output function available by default.

## Source structure

- Blocks always use braces.
- Trailing semicolons may be omitted.
- The official formatter uses two-space indentation and omits the default `public` modifier.
- Strings use double quotes, backslash escapes, and `${expression}` for typed interpolation.
- The current lexer does not support source comments; see [Status](/status) for the exact boundary.

## Run

After obtaining the CLI from a release, run:

```shell
norm run hello.norm
```

For editor installation and usage, see [Tooling](/tooling/). For the complete lexical rules, see [Lexical structure](/spec/grammar/lexical).

To build a single file as a Windows program that does not require Norm to be installed:

```shell
norm build hello.norm
```

This produces `hello.norm.exe` in the same directory. See [Application builds](/tooling/application-build) for project output rules and the offline execution contract.

Next: [Values and bindings](/learn/bindings).
