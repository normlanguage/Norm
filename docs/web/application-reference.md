# Norm Web Application Reference Architecture

## Layers

A standard Norm application uses:

```
HTTP
 |
Router
 |
Controller
 |
Service
 |
Repository
 |
Database
```

## Controller

Controllers translate transport requests into application calls.

## Service

Services contain business rules.

Business failures use Result.

## Repository

Repositories abstract persistence.

## Transaction

Transactions are explicit runtime concerns.

## Deployment

Recommended deployment:

```
Norm source
 -> build
 -> native executable
 -> container
```

