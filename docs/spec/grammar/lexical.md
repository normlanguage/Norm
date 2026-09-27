# Lexical Rules

Norm source files use UTF-8. The compiler decodes a file into Unicode text before establishing source positions.

## Whitespace and newlines

Spaces, tabs, and newlines separate tokens. A newline usually does not end an expression; grammar structure and operators determine whether it is complete. See [`ref<T>` reference syntax](/spec/grammar/references) for a statement boundary involving line-initial dereferences. An ending semicolon may be omitted. The first specification discourages several statements on one line.

[Block call chains](/spec/grammar/functions-advanced#block-call-chains) require their connecting head to remain on the same line; a newline ends eligibility for a connection without a dot.

## Identifiers

An identifier starts with a Unicode letter or underscore. Later characters may be Unicode letters, decimal digits, or underscores. Keywords cannot be identifiers. Names use NFC normalization; two names equal after normalization are duplicate declarations.

```norm
String displayName
Integer retry_count
```

Public APIs should prefer ASCII identifiers for tool and ecosystem compatibility. The compiler should warn about easily confusable cross-script characters.

## Number and string tokens

A numeric token includes integers, decimal points, exponents, and digit-separating underscores. The literal and expected type determine its concrete type. String uses double quotes and backslash escapes. `${expression}` contains an ordinary Norm expression, while `\$` means a literal `$`. The current lexer does not recognize `//` or `/* */` as comments; see [Status](/status) for delivery state.

## Source positions

`SourceSpan` stores a file and half-open range of UTF-16 code units. Line and column positions use the same unit and align with LSP. A protocol needing original-file byte positions must separately derive a byte range from the UTF-8 input, not conflate it with `SourceSpan`.
