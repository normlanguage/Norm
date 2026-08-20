# Norm Formal Language Semantics

This document defines the formal semantic model of Norm programs, including declaration binding, expression evaluation, value production, and runtime behavior.

## Goals

Norm semantics prioritize predictability. Every language feature should have a clear static meaning and a clear runtime representation.

## Evaluation Model

Expressions are typed before execution. The compiler verifies:

- type correctness
- null safety
- initialization
- overload selection
- generic constraints

## Control Expressions

`if`, `for`, and `switch` may produce values. A value-producing control expression requires every reachable path to produce a compatible value using `break value`.

