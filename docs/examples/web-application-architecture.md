# Norm Complete Web Application Architecture

A production application consists of:

- API layer
- business layer
- persistence layer
- infrastructure layer

Example structure:

```
app/
 controller/
 service/
 repository/
 model/
 config/
 test/
```

The framework prefers explicit dependency graphs.
