# SQL API Specification

## Design

Norm SQL provides typed database access without hiding SQL semantics.

## Core Types

Database
Connection
Transaction
Row
Query

## Example

```norm
User user = database.queryOne(
 sql = "select id,name from users where id=:id"
)
```

## Transaction

Business failures use Result<T,E>. Database failures use exceptions or DatabaseError.
