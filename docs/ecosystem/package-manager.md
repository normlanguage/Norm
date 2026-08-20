# Norm Package Manager Design

## Goals

The package manager must support:

- Norm packages
- Java ecosystem compatibility
- Native compatibility metadata

## Package Model

Example:

```toml
[name]
name = "example"
version = "1.0"
```

## Compatibility Layer

Early ecosystem strategy:

```
Norm API
 ↓
Adapter
 ↓
Existing Java library
```

Later components can be replaced by native implementations.

