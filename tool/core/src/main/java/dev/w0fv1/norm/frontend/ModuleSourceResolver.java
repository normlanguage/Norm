package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.value.SourceFile;
import java.io.IOException;
import java.util.List;

public interface ModuleSourceResolver extends AutoCloseable {
  SourceFile read(String relativePath) throws IOException;

  List<String> list(String relativeDirectory) throws IOException;

  @Override
  default void close() throws IOException {}
}
