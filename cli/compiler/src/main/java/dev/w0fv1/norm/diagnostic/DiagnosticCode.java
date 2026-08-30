package dev.w0fv1.norm.diagnostic;

import java.util.Objects;
import java.util.regex.Pattern;

public record DiagnosticCode(String value) {
  private static final Pattern FORMAT = Pattern.compile("NORM-[A-Z][A-Z0-9]*-[0-9]{4}");

  public DiagnosticCode {
    Objects.requireNonNull(value, "value");
    if (!FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("invalid diagnostic code: " + value);
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
