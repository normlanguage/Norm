package dev.w0fv1.norm.jvm;

import java.util.Objects;

public record JavaApiIssue(JavaApiIssueCode code, String detail) {
  public JavaApiIssue {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(detail, "detail");
  }
}
