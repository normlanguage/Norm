# Norm Grammar Reference

## Purpose

This document defines the formal grammar direction of Norm. The grammar prioritizes readability and explicit semantics.

## Lexical rules

Norm uses identifiers, literals, keywords and operators. Keywords are intentionally limited.

Core keywords:

- class
- value
- interface
- extends
- implements
- if
- else
- for
- switch
- case
- break
- continue
- return
- try
- catch
- finally
- throw
- annotation
- reflect

## Declaration model

Example:

```norm
class User {
    String name

    String display() {
        return name
    }
}
```

## Expression model

Control expressions explicitly produce values through break.

```norm
String result = if condition {
    break "yes"
} else {
    break "no"
}
```

No implicit last-expression return exists.

## Future formalization

The compiler will define an EBNF grammar and validate every syntax rule.

