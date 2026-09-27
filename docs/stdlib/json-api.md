# JSON API

`std.json` implements the format-independent serialization interfaces and provides a `JsonValue` tree for dynamic JSON. [`json.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/json/json.norm) defines its public signatures; see [Serialization](/stdlib/serialization) for the shared annotations and mapper interfaces.

```norm
@Serializable()
value User {
  @SerialName(name: "user_name")
  String name
}

String encoded = User(name: "Norm").toJson()
User decoded = encoded.fromJson<User>()
```

Automatic structural serialization accepts only explicitly marked `value` types. `@SerialName` renames a field; `@SerialIgnore` is allowed only on nullable fields, which decode to `null`. Nested values, nullable types, arrays/lists, `Map<String, T>`, enums without payloads, and basic scalars recursively use the same exact type shape.

`fromJson<T>` strictly rejects unknown fields, duplicate fields, missing non-nullable fields, numeric overflow, and trailing content. Parsing, shape, and resource-limit failures all throw `JsonException`; its `code`, `path`, `offset`, `line`, and `column` help locate data errors. Target values are created through their canonical constructors, so field interceptors and validation constraints are not bypassed.

See [HTTP API](/stdlib/http) for HTTP integration and the [serialization runtime](/design/serialization-runtime) for runtime boundaries.

`jsonSchema(type:)` and `functionSchema(operation:)` derive JSON Schema from the same serialization shape. `invokeJson(operation:, arguments:)` validates and decodes a JSON object against a function signature, invokes a top-level function or bound method, and encodes the result. Omitted arguments use declared defaults; nullable arguments without defaults use `null`. Unknown, duplicate, missing, or mistyped arguments are rejected before invocation. Exceptions thrown by the function propagate unchanged. See [JsonFunctionExecutionTest](../../cli/compiler/src/test/java/dev/w0fv1/norm/truffle/JsonFunctionExecutionTest.java) for execution tests.
