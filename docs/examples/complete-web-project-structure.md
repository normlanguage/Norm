# Complete Web Project Structure

Example project:

```
app/
├── controller/
├── service/
├── repository/
├── model/
├── config/
├── migration/
├── tests/
└── main.norm
```

Runtime flow:

HTTP -> Router -> Controller -> Service -> Repository -> Database

The project uses Result for business failures and exceptions for system failures.
