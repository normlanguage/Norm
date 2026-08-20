# Norm Standard Library Core

Norm standard library provides stable application foundations.

## String

String is immutable value data.

Features:

- Unicode support
- interpolation
- formatting
- searching
- splitting
- encoding conversion

Example:

```
"Hello ${user.name}"
```

## Time

Time provides:

- Instant
- Date
- Time
- Duration
- TimeZone

Designed for business applications.

## IO

Provides:

- File
- Stream
- Path
- Reader
- Writer

## JSON

Uses runtime metadata:

```
Json.encode(value)
Json.decode<User>(json)
```

## HTTP

Provides client and server abstractions.

## SQL

Database API hides drivers:

```
Database
Connection
Transaction
Row
```

First implementation can use JDBC adapters.
