# Grammar Reference Overview

This directory describes lexical tokens and grammatical structure in Norm source. The notation is close to EBNF; the first compiler implementation needs to make every production a machine-testable grammar.

## Notation

```text
"token"      fixed keyword or symbol
Name         another production
A?           optional
A*           zero or more
A+           one or more
A | B        either choice
```

## Source files

```text
SourceFile := (AnnotationUse* PackageDeclaration)? Import* Declaration*
```

A package appears at the start of the file; imports precede other declarations. A file without a package is a single-file script. Top-level source permits types, functions, and compile-time constants, but not arbitrary execution statements. `module.norm` in a module's root package directory uses the same `SourceFile` syntax and produces a module definition through `Module module()`.

## Declarations

```text
Declaration := ClassDeclaration
             | ValueDeclaration
             | InterfaceDeclaration
             | EnumDeclaration
             | AnnotationDeclaration
             | FunctionDeclaration
```

Norm puts types first: `String name`, `Integer parse(String text)`. Generic parameters follow the declared name; the nullable marker follows the complete type.

## Expressions and statements

Literals, names, member access, calls, indexing, operators, and control expressions produce values. Variable declarations, assignment, return, and throw are statements. In a value position, if, for, and switch explicitly produce a result through `break value`.

## Related sections

- [Lexical rules](/spec/grammar/lexical)
- [Declarations](/spec/grammar/declarations)
- [Types](/spec/grammar/types)
- [Expressions](/spec/grammar/expressions)
- [Statements](/spec/grammar/statements)
- [Module configuration](/spec/grammar/modules)
- [Operator precedence](/spec/grammar/operators-precedence)
- [`ref<T>` references](/spec/grammar/references)
- [Declaration references and reflection](/spec/declaration-references)
