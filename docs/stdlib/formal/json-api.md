# Norm JSON Specification

## Design Goals

JSON support is based on runtime metadata instead of external schema generators.

Because Norm supports reified generics:

```norm
List<User>.class
```

serialization can preserve complete type information.

## Encoding

Example:

```norm
String json = Json.encode(value = user)
```

## Decoding

```norm
User user = Json.decode<User>(value = json)
```

## Annotation Integration

Fields can customize representation through annotation metadata.

The JSON system remains explicit and does not modify program semantics.
