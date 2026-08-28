package dev.w0fv1.norm.execution;

public interface HttpTransport {
  PlatformHttpResponse send(PlatformHttpRequest request, OperationControl control);
}
