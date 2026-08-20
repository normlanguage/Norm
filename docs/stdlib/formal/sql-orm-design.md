# SQL ORM and Query Builder Design

Norm SQL supports both explicit SQL and typed query building.

Example:

```norm
User user = query(User)
    .where(User.id == id)
    .one()
```

ORM principles:

- SQL remains visible
- mappings are explicit
- transactions are explicit
- no hidden database behavior

@Entity provides metadata only.
