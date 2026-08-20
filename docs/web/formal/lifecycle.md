# Norm Web Request Lifecycle

Request flow:

Client -> Server -> Middleware -> Authentication -> Routing -> Controller -> Service -> Repository -> Response

Controllers translate HTTP concepts into application calls.

Business failures use Result.
Unexpected failures use Exception.

Middleware can inspect, reject, transform, or continue requests.
