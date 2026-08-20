# Norm Standard Collections Design

## Philosophy

Collections are value containers by default.

```
List&lt;User&gt; a = users
List&lt;User&gt; b = a
```

has value semantics.

The runtime may use copy-on-write internally.

## List

Example:

```norm
List&lt;String&gt; names = List&lt;String&gt;(
    values = ["Alice", "Bob"]
)
```

## Map

Maps are typed:

```norm
Map&lt;String, User&gt; users
```

Raw containers are forbidden.

## Set

Set provides unique values based on equality semantics.

