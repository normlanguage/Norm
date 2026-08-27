package dev.w0fv1.norm.execution;

import java.nio.charset.Charset;

@FunctionalInterface
public interface FileSystem {
  String readText(String path, Charset encoding);
}
