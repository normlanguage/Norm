# Serialization

`std.serialization` defines format-independent mapping contracts and shared metadata. [`core.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/serialization/core.norm) defines its public signatures.

```norm
@Serializable()
value Message {
  @SerialName(name: "message_text")
  String text
}

Void roundTrip(DataMapper mapper) {
  DataWriter<Message> writer = mapper.writer<Message>()
  DataReader<Message> reader = mapper.reader<Message>()
  Message decoded = reader.readString(source: writer.writeString(value: Message(text: "Norm")))
}
```

`DataMapper`, `DataReader<T>`, and `DataWriter<T>` are the application's only format abstractions; JSON, XML, and YAML implement each of them. `@Serializable`, `@SerialName`, and `@SerialIgnore` describe shared structure, while format-specific metadata remains in the corresponding format package.

The runtime compiles and caches reader/writer plans only for an exact `CoreType`. Automatic structural mapping handles values only; class identity, object graphs, cycles, and polymorphism require separate protocols. Failures throw typed exceptions for the corresponding format.

See the [JSON API](/stdlib/json-api), [XML API](/stdlib/xml-api), and [YAML API](/stdlib/yaml-api) for format entry points, and the [serialization runtime](/design/serialization-runtime) for internal boundaries.
