package dev.w0fv1.norm.execution;

import java.net.URI;
import java.util.List;
import java.util.Objects;

public final class NormExecutionException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final RuntimeErrorCode code;
  private final URI uri;
  private final int line;
  private final int column;
  private final transient List<GuestStackFrame> guestStack;

  public NormExecutionException(
      RuntimeErrorCode code,
      String message,
      URI uri,
      int line,
      int column,
      List<GuestStackFrame> guestStack,
      Throwable cause) {
    super(message, cause);
    this.code = Objects.requireNonNull(code, "code");
    this.uri = Objects.requireNonNull(uri, "uri");
    this.line = line;
    this.column = column;
    this.guestStack = List.copyOf(guestStack);
  }

  public RuntimeErrorCode code() {
    return code;
  }

  public URI uri() {
    return uri;
  }

  public int line() {
    return line;
  }

  public int column() {
    return column;
  }

  public boolean isGuestException() {
    return true;
  }

  public List<GuestStackFrame> guestStack() {
    return guestStack;
  }
}
