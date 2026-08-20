# Norm Web Security Design

## Authentication

Authentication is an application concern built on explicit components.

Supported models:

- session authentication
- token authentication
- OAuth adapters

## Authorization

Authorization can be expressed with annotations and explicit checks.

## Validation

Validation runs before business logic.

Invalid user input should usually return Result rather than throw exceptions.

## Transaction Safety

Database transactions belong to infrastructure layers.

Business failures should remain Result values.

Unexpected system failures use exceptions.
