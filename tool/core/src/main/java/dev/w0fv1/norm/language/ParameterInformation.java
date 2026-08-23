package dev.w0fv1.norm.language;

import java.util.Objects;

public record ParameterInformation(String label, String documentation) {
  public ParameterInformation {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(documentation, "documentation");
  }
}
