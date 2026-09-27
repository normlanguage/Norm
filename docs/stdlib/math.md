# Math

The Math module provides numeric functions with fixed semantics. It is a collection of top-level functions and needs no `Math` utility class or static methods.

The current implementation includes the integer functions @std.math.integer.abs#1, @std.math.integer.min#1, @std.math.integer.max#1, @std.math.integer.clamp#1, and @std.math.integer.sign#1. The remaining functions below belong to the 1.0 API design.

`clamp` requires `minimum <= maximum`.

```norm
import std.math.clamp

Integer opacity = clamp(value: input, minimum: 0, maximum: 100)
```

## Function groups

- Basics: `abs`, `min`, `max`, `clamp`, `sign`;
- Rounding: `floor`, `ceiling`, `truncate`, `round`;
- Powers and logarithms: `sqrt`, `pow`, `exp`, `log`;
- Trigonometry: `sin`, `cos`, `tan`, and inverse functions.

Trigonometric functions use radians. Explicit functions such as `degreesToRadians` perform angle conversion.

## Special values

Float/Double follow the selected IEEE 754 subset. Comparisons involving NaN, Infinity, and signed zero must be fixed in the numeric specification. Optimization level must not change the integer overflow policy.

The planned Decimal type will have its own rounding API and will not implicitly call binary floating-point Math. Statistics, matrices, and arbitrary-precision algorithms belong in separate libraries to keep the core module bounded.
