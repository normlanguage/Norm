# Norm Compiler Semantic Analysis

## Compilation Phases

Norm compilation performs:

1. Parsing
2. Symbol resolution
3. Type checking
4. Generic resolution
5. Null analysis
6. Control flow analysis
7. IR generation

## Symbol Resolution

Every identifier is resolved before optimization.

The compiler tracks:

- scope
- declaration location
- type information
- visibility

## Null Analysis

The compiler proves that non-null values cannot contain null.

Example:

```norm
String name = loadName()
```

requires `loadName` to return a non-null String.

## Definite Initialization

Class fields must be initialized on every constructor path.
