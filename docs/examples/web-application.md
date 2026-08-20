# Norm Web Example Application

This document describes a complete application architecture.

Layers:

- HTTP layer
- Controller layer
- Service layer
- Repository layer
- Database layer

Example flow:

Request -> Router -> Controller -> Service -> Repository -> Database

Business errors use Result<T,E>.
System failures use Exception.
