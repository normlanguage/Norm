# Norm Web Platform Design

## Goal

Norm should provide a practical application framework without requiring a large magic framework.

## Components

- HTTP server
- Router
- Controller
- Service
- Repository
- Middleware
- Configuration
- Authentication

## Annotation based routing

Example:

```norm
@Get(path="/users/{id}")
HttpResponse&lt;User&gt; getUser(long id) {
}
```

Annotations are explicit and implemented through reflect hooks.

## Dependency management

Dependencies should be explicit through constructors and Ref where shared services are required.

