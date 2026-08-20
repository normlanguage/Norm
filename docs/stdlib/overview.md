# Norm Standard Library Design

## Philosophy

The standard library follows the language philosophy: predictable, explicit and application oriented.

## Core modules

- core
- collections
- text
- time
- io
- json
- http
- sql
- crypto
- testing
- logging

## Collections

Collections are value containers by default.

```norm
List<User> users = List<User>(
    values = []
)
```

Shared mutable collections require Ref.

## Database

Initial implementation can use adapters over mature ecosystems.

```text
Norm SQL API
    ↓
JDBC adapter
    ↓
Database driver
```

Future implementations may replace adapters with native drivers.
