# Norm Web Framework Architecture

## Goal

Norm web development should be explicit, strongly typed, and suitable for production applications.

Layers:

```
HTTP
 ↓
Router
 ↓
Controller
 ↓
Service
 ↓
Repository
 ↓
Database
```

## Annotation Based Registration

Annotations describe metadata.
Reflect hooks perform explicit registration.

## Database

The first implementation can use adapters over existing ecosystems such as JDBC.

The API remains Norm-native.
