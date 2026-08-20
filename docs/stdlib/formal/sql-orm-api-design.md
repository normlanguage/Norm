# SQL ORM API Design

Norm ORM keeps SQL visible.

```
Repository
   |
QueryBuilder
   |
Database Adapter
```

Example:

```norm
User user = db.query(User).where(id = id).one()
```
