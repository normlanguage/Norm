# HTTP Router Implementation Model

## Routing Tree

Routes are registered from annotations and reflect metadata.

## Matching

Method, path parameters, middleware and handler are resolved before execution.

## Handler Pipeline

Request -> Middleware -> Controller -> Service -> Response
