package dev.w0fv1.norm.platform.process;

public final class PlatformProcessException extends RuntimeException {
  private static final long serialVersionUID = 1L;
  private final ProcessFailure reason;
  private final String executable;

  public PlatformProcessException(ProcessFailure reason, String executable, Throwable cause) {
    super("Process " + reason.name() + ": " + executable, cause);
    this.reason = reason;
    this.executable = executable;
  }

  public ProcessFailure reason() {
    return reason;
  }

  public String executable() {
    return executable;
  }
}
