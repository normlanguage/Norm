package dev.w0fv1.norm.platform.http;

import dev.w0fv1.norm.platform.OperationControl;

public interface HttpTransport {
  PlatformHttpResponse send(PlatformHttpRequest request, OperationControl control);
}
