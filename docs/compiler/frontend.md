# Norm Compiler Frontend Design

## Compilation Pipeline

The compiler is divided into clear phases.

```
Source
  ↓
Lexer
  ↓
Parser
  ↓
AST
  ↓
Symbol Resolution
  ↓
Type Checking
  ↓
Typed IR
  ↓
Backend
```

## Lexer

Responsible for:

- keywords
- identifiers
- literals
- operators

## Parser

Produces an abstract syntax tree representing Norm semantics.

## Semantic Analysis

Includes:

- overload resolution
- generic checking
- null analysis
- access checking
- inheritance validation

## Backend Strategy

The same typed IR can target:

- Truffle interpreter
- future native compiler
