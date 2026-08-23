package dev.w0fv1.norm.language;

import java.util.List;
import java.util.Objects;

public record SignatureInformation(
    String label, String documentation, List<ParameterInformation> parameters) {
  public SignatureInformation {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(documentation, "documentation");
    parameters = List.copyOf(parameters);
  }
}
