package dev.w0fv1.norm.cli.value;

public final class ExitCode {
  public static final int SUCCESS = 0;
  public static final int COMPILATION_ERROR = 1;
  public static final int USAGE_ERROR = 2;
  public static final int RUNTIME_ERROR = 3;
  public static final int INTERNAL_ERROR = 70;
  public static final int INPUT_ERROR = 74;

  private ExitCode() {}
}
