# String

String is an immutable Unicode text value. Its content cannot change after construction; slicing and replacement return new Strings. An implementation may share underlying storage but must not expose a mutable view.

```norm
String language = "Norm"
String message = "Hello, " + language
```

## Length and indexing

Text has different units, including bytes, Unicode code points, and grapheme clusters. The API distinguishes them through `byteSize()`, `codePointSize()`, and `graphemeSize()`. It offers no ambiguous `size()` or `length`, nor does it promise that `text[index]` is the index-th character a user sees.

`CodePoint` is a distinct Unicode scalar type. A character literal contains exactly one code point:

```norm
CodePoint letter = 'N'
CodePoint emoji = '😀'
Integer scalar = emoji.scalarValue()
Boolean digit = emoji.isDecimalDigit()
Boolean asciiDigit = letter.isAsciiDigit()
```

`CodePoint` provides `isDecimalDigit()`, `isAsciiDigit()`, `asciiDigitValue()`, `isLetter()`, `isWhitespace()`, `isUppercase()`, and `isLowercase()`. `isAsciiDigit()` accepts only `0` through `9`; `asciiDigitValue()` returns the corresponding integer and produces `INVALID_ARGUMENT` for other input. Case mapping belongs to String because mapping one code point can produce several code points.

Choose a unit explicitly before random access:

```norm
Array<CodePoint> points = text.codePoints()
Array<String> graphemes = text.graphemes()
String part = text.sliceCodePoints(start: 1, end: 4)
String visiblePart = text.sliceGraphemes(start: 1, end: 4)
```

`codePoints()` and `graphemes()` return independent value arrays. Modifying an array does not change the original String. `sliceCodePoints` uses a half-open code-point range.

## State and comparison

```norm
Boolean empty = text.isEmpty()
Integer order = text.compareCodePoints(right: other)
Boolean headerMatches = text.equalsIgnoreCaseAscii(other: "content-type")
```

`compareCodePoints` returns `-1`, `0`, or `1`. `equalsIgnoreCaseAscii` folds ASCII case only; it is suitable for protocol identifiers and independent of the system locale.

## Searching and splitting

`contains`, `startsWith`, `endsWith`, and `split` use exact text matching.

```norm
Boolean present = text.contains(value: "Norm")
Boolean prefix = text.startsWith(prefix: "No")
Boolean suffix = text.endsWith(suffix: "rm")
Array<String> components = path.split(separator: "/")
```

`split` retains empty components produced by leading, trailing, or adjacent separators.

## Replacement and whitespace

```norm
String all = text.replace(target: "old", replacement: "new")
String first = text.replaceFirst(target: "old", replacement: "new")
String clean = text.trim()
String left = text.trimStart()
String right = text.trimEnd()
```

Replacement matches literally; an empty target is an invalid argument. The trim family uses Unicode whitespace without consulting the system locale.

## Case and normalization

```norm
String lower = text.toLowercase()
String upper = text.toUppercase()

import std.text.Normalization
import std.text.isNormalized
import std.text.normalize

String normalized = normalize(value: text, form: Normalization.Nfc)
Boolean canonical = isNormalized(value: normalized, form: Normalization.Nfc)
```

Case conversion without a locale parameter uses stable, locale-independent Unicode rules. `Normalization` provides `Nfc`, `Nfd`, `Nfkc`, and `Nfkd`.

## Finding and parsing

An ordinary missing position is represented by a nullable Integer. Text encoding/decoding and parsing that must carry an error reason use the later Bytes and Result APIs.

The target type's parse API parses numbers, UUIDs, and times; String provides no implicit cross-type conversions.

## Text-building functions

`std.text` provides `repeat`, `join`, and `fromCodePoints`, implemented in `std/text/builders.norm`:

```norm
import std.text.join
import std.text.repeat

String line = repeat(value: "-", count: 8)
String text = join(values: names, separator: ", ")
String rebuilt = fromCodePoints(values: points)
```

`text.isBlank` is a read-only Boolean property: true for an empty string or one containing only Unicode whitespace code points. It follows JDK `Character.isWhitespace`, including ideographic space but excluding no-break space U+00A0.
