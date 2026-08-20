# ORM Entity Query Transaction API

## Entity

```norm
@Entity(table="users")
class User {
    long id
    String name
}
```

## Query

```norm
List<User> users = query(User)
    .where(field="name", value="Alice")
    .list()
```

## Transaction

```norm
transaction.run {
    repository.save(user)
    commit()
}
```

ORM keeps SQL visibility and explicit transaction boundaries.
