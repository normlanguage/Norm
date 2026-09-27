# Literals

## Numbers

```norm
Integer count = 42
Long population = 8_100_000_000
Double ratio = 0.125
```

Underscores may occur only between digits; they group digits without changing the value. Without context, an integer uses Integer if it fits, otherwise Long. A decimal literal defaults to Double. A concrete numeric target type takes priority: `Long value = 7` and `Float ratio = 0.125` materialize directly in the target type. The parser retains exact decimal text and performs no floating-point rounding before type solving completes.

## Strings

```norm
String name = "Norm"
String line = "first\nsecond"
String configuration = "\${bbs.greeting:Hello}"
String greeting = "Hello, ${name}!"
```

Single quotes represent one `CodePoint`. Their content must decode to exactly one Unicode code point:

```norm
CodePoint letter = 'a'
CodePoint emoji = '😀'
CodePoint newline = '\n'
```

Strings use double quotes and standard escapes. `${expression}` evaluates the expression and obtains text through its `toString()` contract, from left to right. `\${` retains a literal `${`, useful for configuration placeholders.

Triple quotes `"""` enclose a multiline string, preserving newlines and indentation. Escape and interpolation rules are the same as ordinary strings. One or two consecutive double quotes inside need no escaping; three end the string. Formatting preserves the original multiline content.

```norm
String query = """
  select todo from Todo todo
  order by todo.id
"""
```

## Boolean and Null

`true` and `false` have type Boolean. `null` can occur only where a nullable expected type already exists; it cannot independently infer an arbitrary type. Runtime represents it with a guest null value rather than exposing host-language null as a Norm value.

## Collections

`[1, 2, 3]` is a sequence literal. With expected type `Array<T>` or `List<T>`, it constructs that container directly. With `Iterable<T>`, its element constraint projects to a default `Array<T>`; without a container context it also defaults to `Array<T>`. It does not first construct an Array and then convert to List. The least common type of several concrete numeric leaf types is `Number`.

```norm
Array<Integer> array = [1, 2, 3]
List<Integer> list = [1, 2, 3]
List<Number> numbers = [1, 2.5, 3]
```

An empty `[]` with no element constraint needs a complete type from its assignment, argument, or return position.

Sequence literals support conditional elements, loop elements, and spreading. They apply to Array and List, not only UI:

```norm
List<Integer> source = [1, 2, 3]
List<Integer> values = [
  0,
  if (source.size() > 0) 10 else 20,
  for (value : source) if (value != 2) value * 2,
  ...source
]
```

`if (condition)` contributes one or zero elements; `else` may be omitted. `for (value : source)` contributes its inner element on each iteration and also supports an explicit element type and index variable. `...source` spreads Iterable elements in order. All three may nest. A condition or iterable source evaluates once when its position is reached; an unselected branch does not execute. The loop variable is visible only in the inner element, and each iteration's closure captures an independent element value.

Collection control structures parenthesize their condition or iteration head. To make an ordinary `if` expression one element, use `(if condition { first } else { second })`. An empty iterable source needs a declared element type; a loop variable cannot be inferred from an untyped `[]`.
