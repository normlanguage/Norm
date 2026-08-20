# Norm Web Framework Specification

## Routing

Routes are declared by annotation.

```
@Get(path="/users/{id}")
```

Runtime registration uses reflect hooks.

## Middleware

Middleware receives request and next handler.

Responsibilities:

- logging
- authentication
- compression
- tracing

## Authentication

Supports:

- session
- token
- OAuth adapters
- custom providers

## Transaction

Database transaction is explicit infrastructure.

Example:

```
@Transactional
saveOrder()
```

## Validation

Validation uses annotations and runtime metadata.

Examples:

- Required
- Length
- Range
- Format

Validation errors should normally use Result.
