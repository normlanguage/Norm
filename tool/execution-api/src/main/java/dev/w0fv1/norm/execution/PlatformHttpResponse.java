package dev.w0fv1.norm.execution;

import java.util.List;

public interface PlatformHttpResponse extends PlatformByteReader {
  int statusCode();

  List<PlatformHttpHeader> headers();
}
