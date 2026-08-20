# Norm Statement Grammar

This document defines statement-level grammar for Norm.

## Principles

Statements are explicit execution units. Control expressions may produce values, but value production requires explicit `break value`.

## Statements

Supported core statements:

- variable declaration
- assignment
- expression statement
- return
- throw
- if statement
- for statement
- switch statement
- try/catch/finally

## Return

`return` exits a function. It is not used to produce values from expressions.

## Break

`break value` produces a value from a value-producing control structure.

