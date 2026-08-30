package dev.w0fv1.norm.platform.http;

import dev.w0fv1.norm.platform.file.PlatformByteReader;
import java.util.List;

public interface PlatformHttpResponse extends PlatformByteReader {
  int statusCode();

  List<PlatformHttpHeader> headers();
}
