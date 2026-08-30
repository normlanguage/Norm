package dev.w0fv1.norm.truffle;

final class ResourceCloseException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  ResourceCloseException(String resource, Exception cause) {
    super("failed to close " + resource, cause);
  }
}
