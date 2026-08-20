# Norm Type Inference Formal Rules

This document defines inference rules for generic deduction, overload selection, nullable flow analysis and expression typing.

## Goals

Norm inference must be predictable. The compiler never guesses through unsafe conversions.

## Principles

- Explicit types have priority.
- No implicit nullable conversion.
- No dynamic fallback.
- Ambiguous inference is a compile error.

