# Norm Standard Library API Principles

The standard library follows:

- strong typing
- explicit errors
- value semantics
- predictable behavior

## Error Design

Expected failures use Result.

System failures use Exception.

## Collections

Collections are value containers by default.

Shared mutable collections require Ref.

