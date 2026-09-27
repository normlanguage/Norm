# YAML API

`std.yaml` implements the format-independent serialization interfaces. [`yaml.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/yaml/yaml.norm) defines its public signatures; see [Serialization](/stdlib/serialization) for the shared mapper and annotations.

```norm
@Serializable()
value User {
  @SerialName(name: "user_name")
  String name
}

String encoded = User(name: "Norm").toYaml()
User decoded = encoded.fromYaml<User>()
```

Structural mapping supports nullable values, arrays/lists, `Map<String, T>`, enums without payloads, nested values, and the shared `@SerialName` and `@SerialIgnore` annotations. Output uses stable block-style YAML, omits the document-start marker, and quotes strings that could otherwise be interpreted as another implicit type.

Decoding accepts only a single document and string mapping keys. It strictly rejects unknown, duplicate, and missing fields, numeric overflow, aliases, and explicit tags. Failures throw `YamlException` with stable `code`, `path`, `offset`, `line`, and `column` fields.
