# XML API

`std.xml` implements the format-independent serialization interfaces and provides XML convenience entry points. [`xml.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/xml/xml.norm) defines its public signatures.

```norm
@Serializable()
@SerialName(name: "user")
value User {
  @XmlAttribute()
  Integer id
  @SerialName(name: "display_name")
  String name
}

String encoded = User(id: 7, name: "Norm").toXml()
User decoded = encoded.fromXml<User>()
```

Fields map to child elements by default. Arrays/lists use `item` children, while maps use `entry`, `key`, and `value`. `@XmlAttribute` maps scalar or enum fields to attributes; missing nullable fields decode to `null`. Root and field names reuse `@SerialName`.

Parsing strictly rejects unknown, duplicate, and missing fields, an incorrect root element, numeric overflow, DTDs, and external entities. Failures throw `XmlException` with stable `code`, `path`, `offset`, `line`, and `column` fields.
