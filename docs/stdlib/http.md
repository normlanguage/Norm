# HTTP API

The HTTP module provides typed requests, streaming responses, and a client adapter. [`client.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/http/client.norm) and [`json.norm`](https://github.com/normlanguage/Norm/blob/main/norm/stdlib/std/http/json.norm) define public signatures. An HTTP server, routing, controllers, and authentication have not yet been delivered.

## Client

```norm
HttpRequest request = get(uri: Uri(value: "https://example.com/status"))
HttpResponse response = systemHttpClient().send(
  request: request,
  timeout: duration(seconds: 5, nanoseconds: 0)
)
String body = use<String>(resource: response, body: () {
  decodeText(
    content: readAll(reader: response, maximumBytes: 1048576),
    encoding: TextEncoding.Utf8
  )
})
```

`HttpResponse` implements both `ByteReader` and `Resource`. The timeout passed to `send` is a total budget covering sending, waiting for the response, and reading the body. Execution cancellation propagates to the request and body read. Responses must be closed deterministically through `use` or an explicit `close()`. Redirects are not followed by default.

## Server

No server adapter is in the standard library yet. It will reuse the same `HttpRequest`, headers, body stream, and exception model.

## Types

`HttpMethod`, `Status`, `HeaderMap`, and `Uri` do not expose host types. `HeaderMap` preserves repeated headers; an HTTP status is normal response data.

The JDK adapter uses `java.net.http.HttpClient`. DNS, connection, TLS, protocol, timeout, cancellation, and body I/O failures throw `HttpException`, not `Result`.

JSON requests use `postJson` or `jsonRequest`. They compose with `std.json` at the `Bytes` boundary and consistently write `application/json`. The caller still explicitly controls the response read limit and `decodeJson<T>`.
