package dev.w0fv1.norm.platform.process;

public record ProcessResult(
    ProcessOutcome outcome,
    Integer exitCode,
    byte[] stdout,
    byte[] stderr,
    boolean stdoutTruncated,
    boolean stderrTruncated) {
  public ProcessResult {
    stdout = stdout.clone();
    stderr = stderr.clone();
  }

  @Override
  public byte[] stdout() {
    return stdout.clone();
  }

  @Override
  public byte[] stderr() {
    return stderr.clone();
  }
}
