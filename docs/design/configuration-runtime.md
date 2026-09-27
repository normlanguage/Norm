# Configuration mapping runtime

## Goal

Framework configuration is expressed as an ordinary Norm value. Host properties are derived from one Core structure metadata model. Application declarations, IDE type information, structural serialization, and Java framework startup share the same type definition; framework adapters do not maintain handwritten string property tables.

## Structure rules

- Root and nested values explicitly use `@Serializable`.
- Fields without a rename convert from camelCase to kebab-case.
- `@SerialName` and `@SerialIgnore` are shared with other structural mapping formats.
- Null produces no property.
- List and Array use `[index]`; String Map keys become path segments.
- Enum variants use kebab-case.
- `@ConfigurationKey` expresses a named collection.
- `@ConfigurationValue` expresses a scalar wrapper.

## Runtime boundary

`configurationProperties<T>()` obtains the exact CoreType through reified `Class<T>`, reuses the shape cached by the Serialization Runtime, and reads values by field ordinal. Its output is an insertion-ordered host Map that can cross the Java Binding boundary directly. The implementation does not inspect JVM fields, call getters by string name, or build an intermediate JSON/YAML tree.

A framework module defines only the Norm types corresponding to that framework's configuration hierarchy and a small number of semantic constructors. Its launcher owns lifecycle management and passes the mapped result to the official framework entry point.
