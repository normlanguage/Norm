# ORM Relation Mapping

Relations are explicit:

```
User
  hasMany Orders
Order
  belongsTo User
```

The ORM never hides database behavior.
Transactions remain explicit.
