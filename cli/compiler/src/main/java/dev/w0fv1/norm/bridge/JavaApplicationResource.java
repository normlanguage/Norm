package dev.w0fv1.norm.bridge;

public interface JavaApplicationResource extends AutoCloseable {
  @Override
  void close();
}
